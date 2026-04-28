import {
  createContext,
  startTransition,
  useContext,
  useEffect,
  useMemo,
  useReducer,
} from "react";
import { createWifiRepository, defaultApiBaseUrl } from "../lib/wifiRepository";

const AppStateContext = createContext(null);

const repository = createWifiRepository();

const initialState = {
  apiBaseUrl: defaultApiBaseUrl,
  user: {
    name: "",
    email: "",
  },
  currentDraft: null,
  latestScan: null,
  savedNetworks: [],
  savedSummary: {
    count: 0,
    latestSsid: null,
  },
  health: {
    ok: false,
    error: "",
    service: "",
  },
  busy: false,
};

function reducer(state, action) {
  switch (action.type) {
    case "hydrate":
      return { ...state, ...action.payload };
    case "set-busy":
      return { ...state, busy: action.payload };
    case "set-user":
      return { ...state, user: action.payload };
    case "set-api-base-url":
      return { ...state, apiBaseUrl: action.payload };
    case "set-health":
      return { ...state, health: action.payload };
    case "set-current-draft":
      return { ...state, currentDraft: action.payload };
    case "set-saved-data":
      return {
        ...state,
        savedNetworks: action.payload.savedNetworks,
        savedSummary: action.payload.savedSummary,
        latestScan: action.payload.latestScan,
      };
    default:
      return state;
  }
}

export function AppStateProvider({ children }) {
  const [state, dispatch] = useReducer(reducer, initialState);

  useEffect(() => {
    async function hydrate() {
      const [user, latestScan, savedNetworks, savedSummary] = await Promise.all([
        repository.getUser(),
        repository.getLatestSavedWifi(),
        repository.getSavedNetworks(),
        repository.getSavedNetworksSummary(),
      ]);

      startTransition(() => {
        dispatch({
          type: "hydrate",
          payload: {
            user,
            latestScan,
            savedNetworks,
            savedSummary,
          },
        });
      });
    }
    hydrate();
  }, []);

  async function refreshSavedData() {
    const [latestScan, savedNetworks, savedSummary] = await Promise.all([
      repository.getLatestSavedWifi(),
      repository.getSavedNetworks(),
      repository.getSavedNetworksSummary(),
    ]);

    dispatch({
      type: "set-saved-data",
      payload: { latestScan, savedNetworks, savedSummary },
    });
  }

  async function checkApiHealth() {
    try {
      dispatch({ type: "set-busy", payload: true });
      const health = await repository.checkHealth(state.apiBaseUrl);
      dispatch({ type: "set-health", payload: { ...health, error: "" } });
      return health;
    } catch (error) {
      const payload = {
        ok: false,
        service: "",
        error: error instanceof Error ? error.message : "Unable to reach backend",
      };
      dispatch({ type: "set-health", payload });
      throw error;
    } finally {
      dispatch({ type: "set-busy", payload: false });
    }
  }

  async function parseScan({ ocrText, sourceLabel }) {
    dispatch({ type: "set-busy", payload: true });
    try {
      const envelope = await repository.parseOcr(state.apiBaseUrl, ocrText);
      if (!envelope.ok || !envelope.data) {
        throw new Error(envelope.error || "Parse failed");
      }

      const savedRecord = await repository.saveParsedWifi(
        state.apiBaseUrl,
        ocrText,
        envelope.data,
      );

      const draft = {
        ssid: envelope.data.ssid || "",
        password: envelope.data.password || "",
        security: envelope.data.security || "WPA/WPA2",
        sourceFormat: envelope.data.sourceFormat || sourceLabel,
        confidence: envelope.data.confidence ?? null,
        passwordOnly: Boolean(envelope.data.passwordOnly),
        ocrText,
        createdAtMillis: savedRecord.createdAtMillis,
      };

      dispatch({ type: "set-current-draft", payload: draft });
      await refreshSavedData();
      return draft;
    } finally {
      dispatch({ type: "set-busy", payload: false });
    }
  }

  function saveManualDraft(draft) {
    dispatch({
      type: "set-current-draft",
      payload: {
        ...draft,
        sourceFormat: draft.sourceFormat || "manual_entry",
        confidence: null,
        passwordOnly: false,
        ocrText: "",
        createdAtMillis: Date.now(),
      },
    });
  }

  function updateCurrentDraft(patch) {
    dispatch({
      type: "set-current-draft",
      payload: {
        ...state.currentDraft,
        ...patch,
      },
    });
  }

  async function connectCurrent({ savePassword, passphrase }) {
    if (!state.currentDraft) {
      throw new Error("No Wi-Fi draft to save");
    }

    dispatch({ type: "set-busy", payload: true });
    try {
      const record = await repository.saveConnectedWifi({
        ssid: state.currentDraft.ssid,
        password: state.currentDraft.password,
        security: state.currentDraft.security || "WPA/WPA2",
        sourceFormat: state.currentDraft.sourceFormat || "manual_entry",
        savePassword,
        passphrase,
      });

      await refreshSavedData();
      return record;
    } finally {
      dispatch({ type: "set-busy", payload: false });
    }
  }

  async function decryptNetworkPassword(id, passphrase) {
    return repository.decryptNetworkPassword(id, passphrase);
  }

  async function deleteSavedNetworkById(id) {
    await repository.deleteSavedNetworkById(id);
    await refreshSavedData();
  }

  async function clearSavedNetworks() {
    await repository.clearSavedNetworks();
    await refreshSavedData();
  }

  async function validateCurrentDraft() {
    if (!state.currentDraft) {
      throw new Error("No Wi-Fi draft to validate");
    }

    dispatch({ type: "set-busy", payload: true });
    try {
      const envelope = await repository.validateWifi(state.apiBaseUrl, {
        ssid: state.currentDraft.ssid,
        password: state.currentDraft.password,
        ocrText: state.currentDraft.ocrText,
      });

      if (!envelope.ok) {
        throw new Error(envelope.error || "Validation failed");
      }

      return envelope.data;
    } finally {
      dispatch({ type: "set-busy", payload: false });
    }
  }

  function saveUser(user) {
    repository.saveUser(user);
    dispatch({ type: "set-user", payload: user });
  }

  function setApiBaseUrl(nextBaseUrl) {
    repository.saveApiBaseUrl(nextBaseUrl);
    dispatch({ type: "set-api-base-url", payload: nextBaseUrl });
  }

  function logout() {
    repository.saveUser({ name: "", email: "" });
    dispatch({ type: "set-user", payload: { name: "", email: "" } });
  }

  const value = useMemo(
    () => ({
      state,
      actions: {
        checkApiHealth,
        parseScan,
        saveManualDraft,
        updateCurrentDraft,
        connectCurrent,
        validateCurrentDraft,
        decryptNetworkPassword,
        deleteSavedNetworkById,
        clearSavedNetworks,
        refreshSavedData,
        saveUser,
        setApiBaseUrl,
        logout,
      },
    }),
    [state],
  );

  return <AppStateContext.Provider value={value}>{children}</AppStateContext.Provider>;
}

export function useAppState() {
  const context = useContext(AppStateContext);
  if (!context) {
    throw new Error("useAppState must be used within AppStateProvider");
  }
  return context;
}