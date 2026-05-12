const { parseOcrWifiData } = require("./ocrParser");

describe("parseOcrWifiData", () => {
  test("parses noisy OCR labels with digit substitutions", () => {
    const result = parseOcrWifiData("W1F1 ： The Monday Coffee\nPA55 ： TheMondayCoffee");

    expect(result.ok).toBe(true);
    expect(result.data.ssid).toBe("The Monday Coffee");
    expect(result.data.password).toBe("TheMondayCoffee");
    expect(result.data.passwordOnly).toBe(false);
  });

  test("parses split-row cafe board layouts", () => {
    const result = parseOcrWifiData("wifi\nName : Password :\nCAFE MỘC    Cf222222");

    expect(result.ok).toBe(true);
    expect(result.data.ssid).toBe("CAFE MỘC");
    expect(result.data.password).toBe("Cf222222");
    expect(result.data.passwordOnly).toBe(false);
  });

  test("parses label-only ID rows followed by SSID and password values", () => {
    const result = parseOcrWifiData("Wi-Fi\nID\nbepbaha\nbabaloveu");

    expect(result.ok).toBe(true);
    expect(result.data.ssid).toBe("bepbaha");
    expect(result.data.password).toBe("babaloveu");
    expect(result.data.passwordOnly).toBe(false);
  });

  test("ignores trailing business text after password lines", () => {
    const result = parseOcrWifiData(
      "THÔNG TIN\nWIFI : Chú Mập\nPass: xincamon\nOPEN: 4 PM-12AM\nGrabFood, BeFood, ShoppeFood:",
    );

    expect(result.ok).toBe(true);
    expect(result.data.ssid).toBe("Chú Mập");
    expect(result.data.password).toBe("xincamon");
    expect(result.data.passwordOnly).toBe(false);
  });

  test("parses pass wifi label from small sign text", () => {
    const result = parseOcrWifiData("Wifi: Nguyễn Hoàng Bakery 2.4g\nPASS WIFI: 2014bakery");

    expect(result.ok).toBe(true);
    expect(result.data.ssid).toBe("Nguyễn Hoàng Bakery 2.4g");
    expect(result.data.password).toBe("2014bakery");
    expect(result.data.passwordOnly).toBe(false);
  });

  test("treats a single useful OCR line as password", () => {
    const result = parseOcrWifiData("99hoanghoatham");

    expect(result.ok).toBe(true);
    expect(result.data.ssid).toBeNull();
    expect(result.data.password).toBe("99hoanghoatham");
    expect(result.data.passwordOnly).toBe(true);
  });

  test("parses unlabeled SSID and password rows", () => {
    const result = parseOcrWifiData("CAFE61\n61616161");

    expect(result.ok).toBe(true);
    expect(result.data.ssid).toBe("CAFE61");
    expect(result.data.password).toBe("61616161");
    expect(result.data.passwordOnly).toBe(false);
  });

  test("expands clear repeated password patterns", () => {
    expect(parseOcrWifiData("68 4 lần").data.password).toBe("68686868");
    expect(parseOcrWifiData("Pass: 68 x4").data.password).toBe("68686868");
    expect(parseOcrWifiData("password: abc 3 lần").data.password).toBe("abcabcabc");
    expect(parseOcrWifiData("wifi123 x2").data.password).toBe("wifi123wifi123");
  });
});
