package com.smartwificonnect.app;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "DeviceWifi")
public class DeviceWifiPlugin extends Plugin {
    @PluginMethod
    public void getStatus(PluginCall call) {
        JSObject result = new JSObject();
        WifiManager wifiManager = (WifiManager) getContext().getApplicationContext()
            .getSystemService(Context.WIFI_SERVICE);

        boolean wifiEnabled = wifiManager != null && wifiManager.isWifiEnabled();

        result.put("wifiEnabled", wifiEnabled);
        result.put("platform", "android");
        call.resolve(result);
    }

    @PluginMethod
    public void openSettings(PluginCall call) {
        Intent intent;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            intent = new Intent(Settings.Panel.ACTION_WIFI);
        } else {
            intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getActivity().startActivity(intent);

        JSObject result = new JSObject();
        result.put("opened", true);
        call.resolve(result);
    }
}