# User Guide: How to Save Wi-Fi Networks

Step-by-step guide for users to save and manage Wi-Fi networks in SmartWiFi-Connect.

---

## 🎯 Quick Start: Save Your First Network

### Option 1: Scan OCR Image (Recommended for printed networks)

1. **Open the app** → SmartWiFi-Connect
2. **Click "Paste OCR text"** in the main menu
3. **Enter or paste** the Wi-Fi details from image
   - Example: `Tên WiFi: CafeGuest` / `Mật khẩu: cafemomo123`
4. **Click "Parse OCR text"**
5. **Review the extracted details** on the Review screen
   - Check SSID (network name)
   - Check Password
   - Check Security type
6. **Choose password save preference:**
   - ☑️ Check "Save password in this browser" (recommended)
   - ☐ Uncheck to save only the network name, not password
7. **Click "Save network"**
8. **See success message:** "Network saved to browser history"
9. **Done!** Network is saved and visible in History

---

### Option 2: Scan QR Code (Fastest for phone Wi-Fi signals)

1. **Open the app** → SmartWiFi-Connect
2. **Click "Parse Wi-Fi QR payload"** 
3. **Paste or enter** the QR code data
   - Format: `WIFI:T:WPA;S:NetworkName;P:Password;;`
4. **Click "Parse QR payload"**
5. **Review** the extracted information
6. **Choose** whether to save password
7. **Click "Save network"**
8. **Network saved!** Appears in History

---

### Option 3: Manual Entry (When you know the details)

1. **Open the app** → SmartWiFi-Connect
2. **Click "Manual entry"**
3. **Enter network details:**
   - SSID (network name): *Required*
   - Password: *Optional*
   - Security: Select from dropdown
4. **Click "Continue"**
5. **Review** the entered information
6. **Choose** password save preference
7. **Click "Save network"**
8. **Success!** Network is saved

---

## 📖 Understanding the Save Screen

### Review Screen Components

```
┌─────────────────────────────────────┐
│ Review Result           [ocr_text]   │  ← Source badge (how it was scanned)
│ CafeGuest                           │
├─────────────────────────────────────┤
│ SSID              [CafeGuest      ] │  ← Can edit here
│ Password          [cafemomo123   ] │  ← Can edit here
│ Security          [WPA/WPA2      ] │  ← Can change security type
│ Confidence        [0.92 / 100%   ] │  ← Quality score (read-only)
├─────────────────────────────────────┤
│ ☑ Save password in this browser      │  ← Check/uncheck for password storage
├─────────────────────────────────────┤
│ OCR / QR input                       │  ← Original scanned text (read-only)
│ Tên WiFi: CafeGuest                 │
│ Mật khẩu: cafemomo123               │
├─────────────────────────────────────┤
│ [Save network]  [Open history]      │  ← Action buttons
└─────────────────────────────────────┘
```

### What Each Field Means

| Field | Meaning | Can Edit? |
|-------|---------|-----------|
| **SSID** | The Wi-Fi network name | ✅ Yes |
| **Password** | The Wi-Fi password | ✅ Yes |
| **Security** | Encryption type (WPA, WEP, Open) | ✅ Yes |
| **Confidence** | How sure the app is about the scan | ❌ No |
| **Source badge** | How you entered the data (OCR/QR/Manual) | ❌ No |

---

## 💾 Saving Password: Safe or Risky?

### ✅ SAFE TO CHECK (Recommended)

Your password is:
- Stored **locally on your device** only (browser localStorage)
- **Never sent to the internet** or our servers
- **Never shared** with anyone
- **Protected by your browser** security settings

✅ **We recommend:** Check the box to save password

### ⚠️ SECURITY TIPS

- Only check on **personal/trusted devices**
- Don't check on **public/shared computers**
- Uncheck on **library/internet café computers**
- The app data is cleared when you clear browser history
- Share Wi-Fi details verbally, not via saved passwords

### ❌ UNCHECK IF

- Using a shared computer
- Concerned about privacy
- Only need the SSID, not password
- Public/work device

---

## 📋 View & Manage Saved Networks

### Access Your History

1. **Open the app**
2. **Click "History"** button
3. **See all saved networks** sorted by most recently used

### History Screen Features

| Feature | How to Use |
|---------|-----------|
| **Network list** | Shows all your saved Wi-Fi networks |
| **Reveal passwords** | Toggle to show/hide passwords |
| **Last connected** | Shows when you last saved it |
| **Security badge** | Shows encryption type (WPA, WEP, etc.) |
| **Delete button** | Remove individual networks |
| **Clear all** | Delete all saved networks at once |

### Example History View

```
History                    [Reveal passwords]  [Clear all]

📡 HomeNetwork
   Source: ocr_text
   Last connected: 28/04/2026, 10:30
   Password: ••••••••••
   Saved: Stored in browser
   [Delete]

📡 CafeGuest  
   Source: manual_entry
   Last connected: 28/04/2026, 09:15
   Password: Not stored
   Saved: Not stored
   [Delete]

📡 OfficeWiFi
   Source: wifi_qr
   Last connected: 27/04/2026, 14:45
   Password: ••••••••••
   Saved: Stored in browser
   [Delete]
```

---

## 🔧 Common Tasks

### Task 1: Re-use a Saved Network

**Problem:** You want to connect to the same network again

**Solution:**
1. Go to **History** screen
2. Find the network in the list
3. Click **"Reveal passwords"** if you need to see the password
4. Manually enter the SSID and password in your device settings
5. Or copy the password from the app

---

### Task 2: Update a Network Password

**Problem:** Network password changed but you have it saved

**Solution:**
1. Go to **"Manual entry"** screen
2. Enter the **new password**
3. Enter the same **SSID**
4. Click **"Continue"**
5. Check **"Save password"** checkbox
6. Click **"Save network"**
7. **Result:** Old password is replaced with new one

---

### Task 3: Stop Saving a Network

**Problem:** Don't want network saved anymore

**Solution:**
1. Go to **History** screen
2. Find the network
3. Click **"Delete"** button
4. Network is removed (cannot be recovered)

---

### Task 4: Clear All Saved Networks

**Problem:** Want to start fresh or privacy concern

**Solution:**
1. Go to **History** screen
2. Click **"Clear all"** button (usually bottom of screen)
3. **All networks deleted** from browser
4. **Confirmation:** Shows empty state message

---

## ❓ FAQ

### Q: Is my password safe in the browser?
**A:** Yes! Passwords are stored locally on your device only. They're never sent to our servers or the internet. They're as safe as your browser security.

### Q: Can other people access my saved networks?
**A:** Not if they don't use your device. If someone uses your device, they can see saved networks in History. Consider clearing history or not saving passwords on shared devices.

### Q: What if I clear browser history?
**A:** Saved networks may be deleted if you clear "cookies and cached data." Our app stores in browser localStorage. Check your browser settings to see what's cleared.

### Q: Can I backup my saved networks?
**A:** Currently, no built-in backup feature. You can write down SSIDs and passwords manually, or use your device's Wi-Fi connection manager.

### Q: Why is my network not showing confidence score?
**A:** Confidence score only shows when parsed from OCR or QR. Manual entry networks don't have a score since you entered the data directly.

### Q: Can I sync networks across devices?
**A:** No. Networks are saved locally on each device. You need to save on each device separately.

### Q: How many networks can I save?
**A:** Up to 50 networks. After that, oldest networks are removed automatically.

### Q: Can I export my saved networks?
**A:** Not yet. This feature is planned for future updates.

---

## 🆘 Troubleshooting

### Problem: Network didn't save

**Check these:**
1. ✅ Click "Save network" button (not "Open history")
2. ✅ Check for red error message at bottom
3. ✅ SSID must not be empty
4. ✅ Password must be 8-63 characters (if entering manually)
5. ✅ Browser storage not full (clear old networks)

**If still not working:**
- Refresh the page
- Try again with different network
- Check browser console (F12) for errors

---

### Problem: Network appears in History but password not saved

**Reason:** You unchecked "Save password" box

**Solution:**
1. Delete the network from History
2. Save it again
3. Make sure to **check** "Save password" box

---

### Problem: Can't see "Saved" status

**Check:**
- Device setting: "Saved: Stored in browser" means password is stored
- Device setting: "Saved: Not stored" means only SSID is saved

---

### Problem: Password is revealing as dots

**Normal behavior:** Passwords display as dots (••••••) for security

**To see password:**
1. Go to History screen
2. Click "Reveal passwords" button
3. Passwords now visible as actual characters
4. Click again to hide

---

## 📱 Platform-Specific Notes

### Web Browser (Windows/Mac/Linux)

✅ **Works:** Save/view/delete networks  
✅ **Works:** Reveal/hide passwords  
✅ **Note:** Saved to browser localStorage  
✅ **Survives:** Browser restarts  
❌ **Lost if:** You clear browser history/cache

### Mobile Browser (Android/iPhone)

✅ **Works:** Same as web browser  
✅ **Works:** On Safari and Chrome  
✅ **Note:** Saved to mobile browser storage  
❌ **Note:** May be cleared if app cache is cleared

### PWA (Home Screen App)

✅ **Works:** All features work  
✅ **Better:** Persistent storage like app  
✅ **Survives:** App cache clears (data persists)  
✅ **Note:** More reliable than browser

---

## 💡 Best Practices

### ✅ DO

- ✅ Save passwords on personal devices
- ✅ Use strong, unique passwords for each network
- ✅ Update saved password if network password changes
- ✅ Clear old unused networks from History
- ✅ Use manual entry for networks you frequently use
- ✅ Check "Reveal passwords" only when needed

### ❌ DON'T

- ❌ Save passwords on shared/public devices
- ❌ Share your saved networks with others
- ❌ Rely on this as backup (export manually instead)
- ❌ Forget to clear history on borrowed devices
- ❌ Save passwords for networks you don't trust
- ❌ Share screenshots of History screen (contains passwords)

---

## 📞 Need Help?

- **In-app Help:** Settings screen has contact info
- **Report Bug:** Use Settings → Report Issue
- **Privacy Concern:** See Privacy Policy in Settings
- **Feature Request:** Submit via Settings → Feedback

---

**Last Updated:** 2026-04-28  
**Version:** 1.0 Complete
