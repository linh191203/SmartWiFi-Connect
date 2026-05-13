# Huong Dan Su Dung SmartWiFi-Connect

## 1. Muc dich cua ung dung
SmartWiFi-Connect la ung dung Android giup nguoi dung ket noi Wi-Fi nhanh hon va it nhap sai hon thong qua 4 cach chinh:

1. Quet ma QR Wi-Fi.
2. Quet anh chua thong tin Wi-Fi bang OCR.
3. Nhap tay SSID, password va kieu bao mat.
4. Xem lai ket qua truoc khi ket noi va luu lich su de dung lai sau nay.

Ung dung phu hop cho cac tinh huong nhu quan cafe, truong hoc, van phong, khu cong cong, noi nguoi dung can ket noi mang nhanh va muon giam thao tac nhap tay.

## 2. Nhung gi can chuan bi truoc khi dung
Truoc khi bat dau, nguoi dung nen kiem tra cac dieu kien sau:

1. May dang bat Wi-Fi.
2. Camera hoat dong binh thuong neu ban muon quet QR hoac chup anh OCR.
3. Ban san sang cap quyen Camera cho app.
4. Neu muon xem Wi-Fi xung quanh, goi y SSID gan dung hoac ket noi mang tren Android moi, ban co the can cap them quyen Location hoac Nearby Wi-Fi.
5. Neu ban dang dung ban demo co backend, may can co ket noi mang den server de app parse OCR va AI validate du lieu.

## 3. Cach khoi dong ung dung lan dau
Luot su dung dau tien thuong dien ra theo trinh tu sau:

1. Mo app tu man hinh chinh cua dien thoai.
2. Man hinh Splash hien logo va ten ung dung trong vai giay.
3. App chuyen sang Onboarding hoac vao thang Home, tuy theo trang thai hien tai cua ban build.
4. Neu co Onboarding, doc cac trang gioi thieu de hieu nhanh gia tri cua app.
5. Bam Bat dau de tiep tuc.
6. Neu build co Login/Register, dang nhap hoac tao tai khoan de vao Home.
7. Neu build co man hinh dong y chinh sach, doc tom tat quyen va chon Dong y de tiep tuc su dung.

## 4. Cac quyen ung dung co the yeu cau
Trong qua trinh su dung, app co the xin cac quyen sau:

1. Camera.
Dung de quet ma QR va chup anh de OCR.

2. Location hoac Nearby Wi-Fi.
Dung de quet Wi-Fi xung quanh, goi y SSID gan dung va ho tro mot so thao tac ket noi tren Android 13+.

3. Quyen lien quan den Wi-Fi.
Dung de doc trang thai mang va yeu cau ket noi Wi-Fi tu ben trong app.

Neu da tu choi quyen truoc do, ban co the vao Cai dat he thong cua Android de cap lai quyen cho app.

## 5. Tong quan man hinh Home
Home la trung tam dieu huong cua ung dung. Tai day, nguoi dung thuong thay:

1. Hero card o phan dau man hinh.
2. Cac action chinh de bat dau quet QR, quet anh OCR hoac nhap tay.
3. Khu vuc lich su gan day de truy cap nhanh vao cac mang da xu ly truoc do.
4. Bottom navigation de di chuyen giua Home, Lich su, Chia se va Cai dat, tuy theo ban build.

Tu Home, nguoi dung co the chon mot trong 4 luong chinh:

1. Quet ma QR Wi-Fi.
2. Quet anh Wi-Fi bang OCR.
3. Nhap tay thong tin mang.
4. Chia se Wi-Fi cho thiet bi o gan.

## 6. Cach dung tinh nang Quet ma QR Wi-Fi
Day la cach nhanh nhat neu dia diem da co san ma QR.

### 6.1. Cac buoc thuc hien
1. Mo app va vao Home.
2. Chon Quet ma QR.
3. Neu day la lan dau, app se xin quyen Camera.
4. Chon Cho phep de bat dau quet.
5. Dua camera vao ma QR sao cho ma nam trong khung scan trong suot.
6. Giu may on dinh trong 1 den 2 giay de ML Kit Barcode Scanning nhan dien ma.
7. Sau khi doc duoc QR, app se chuyen sang man hinh ket qua de hien thi du lieu da nhan duoc.

### 6.2. Luu y khi quet QR
1. Dat ma QR trong moi truong du sang.
2. Khong de nhieu ma QR cung xuat hien trong khung hinh.
3. Giu camera song song voi ma de tranh meo hinh.
4. Neu quet cham, dua camera gan hon mot chut nhung van giu ro net.

### 6.3. Sau khi quet thanh cong
Sau khi QR duoc nhan dien, ban se duoc dua den man hinh ket qua hoac review. Tai day ban co the:

1. Xem SSID, password va security neu du lieu duoc parse thanh cong.
2. Sua lai thong tin neu QR chua dung dinh dang hoac can chinh tay.
3. Ket noi vao mang.
4. Luu lich su de mo lai sau.
5. Sao chep mat khau de dung o app khac neu can.

## 7. Cach dung tinh nang Quet anh Wi-Fi bang OCR
Tinh nang nay dung khi thong tin Wi-Fi nam tren bien bang, menu, sticker, man hinh khac hoac anh chup san.

### 7.1. Cac cach dua anh vao app
App thuong ho tro 2 cach:

1. Chup anh moi bang camera.
2. Chon anh co san tu thu vien.

### 7.2. Cac buoc thuc hien
1. Mo Home.
2. Chon Quet anh Wi-Fi hoac OCR.
3. Chon chup anh hoac lay anh tu thu vien.
4. Neu dung camera, cap quyen Camera khi duoc hoi.
5. Dat khung chup sao cho SSID va password nam ro trong vung anh.
6. Bam chup anh hoac xac nhan anh da chon.
7. App dung ML Kit Text Recognition de doc van ban tu anh.
8. Sau khi OCR xong, app chuyen sang man hinh OCR Result.

### 7.3. Nhung gi xay ra tai man hinh OCR Result
Tai man hinh nay, app co the xu ly theo nhieu tang:

1. Hien thi text OCR goc ma camera vua doc duoc.
2. Cho phep ban sua lai text OCR neu mot vai ky tu bi nhan sai.
3. Goi backend parse qua endpoint `/api/v1/ocr/parse` de tach SSID, password, security va confidence.
4. Goi AI validate qua endpoint `/api/ai/validate` de danh gia do hop ly cua du lieu.
5. Goi fuzzy SSID qua endpoint `/api/v1/ssid/fuzzy-match` neu may duoc cap quyen xem Wi-Fi xung quanh.
6. Hien thi danh sach Wi-Fi lan can de ban so sanh voi ket qua OCR.

### 7.4. Cach doc cac thong tin tren man hinh OCR Result
Ban co the thay cac thanh phan sau:

1. Status message.
Cho biet app dang loading, parse thanh cong, can sua lai, hoac gap loi.

2. OCR text editor.
Noi ban co the sua van ban goc neu OCR nhan nham chu, so hoac ky tu dac biet.

3. Parsed Wi-Fi card.
Hien thi cac truong da duoc tach ra nhu SSID, password, security, do tu tin.

4. AI validation card.
Hien thi danh gia cua lop kiem tra AI, gom confidence, suggestion, parse recommendation va kha nang auto connect.

5. Fuzzy SSID suggestion.
Hien thi goi y SSID gan giong nhat voi ket qua OCR trong khu vuc ban dang dung.

6. Danh sach Wi-Fi xung quanh.
Giup ban doi chieu xem ten mang OCR co giong mang dang phat that hay khong.

### 7.5. Cach chinh sua ket qua OCR cho dung
Neu ket qua OCR chua chinh xac, ban nen lam theo thu tu sau:

1. Kiem tra lai text OCR goc.
2. Sua cac ky tu de nham lan nhu O va 0, I va l, 5 va S.
3. Bam parse lai neu man hinh co nut parse.
4. Xem lai SSID va password sau khi parse.
5. Doi chieu voi danh sach Wi-Fi xung quanh.
6. Neu AI validation dua ra normalized SSID hoac normalized password hop ly hon, can nhac ap dung gia tri do.

### 7.6. Meo de OCR chinh xac hon
1. Chup anh du sang, khong bi rung.
2. Uu tien chup thang truc dien, tranh xien goc.
3. Cat bo cac vung khong lien quan neu anh qua nhieu noi dung.
4. Dam bao SSID va password khong bi tay, loe hoac bi bong den che.
5. Neu anh qua xa, chup lai anh gan hon thay vi co gang sua qua nhieu.

## 8. Cach dung tinh nang Nhap tay thong tin Wi-Fi
Tinh nang nay phu hop khi ban da biet ten mang va mat khau nhung khong co QR hoac anh.

### 8.1. Cac buoc thuc hien
1. Mo Home.
2. Chon Nhap tay.
3. Nhap SSID chinh xac.
4. Nhap password neu mang co mat khau.
5. Chon security type phu hop.
6. Bam tiep tuc de sang man hinh review.

### 8.2. Cach nhap chuan
1. SSID phai giong dung ten mang dang phat, ke ca chu hoa, chu thuong neu can doi chieu.
2. Password can nhap dung tung ky tu.
3. Neu la mang mo, de trong password va chon kieu bao mat phu hop.
4. Neu khong chac security type, co the so sanh voi thong tin app hien thi trong danh sach Wi-Fi xung quanh.

## 9. Man hinh Review Result va cach xac nhan truoc khi ket noi
Sau QR, OCR hoac nhap tay, app thuong dua ban den man hinh review de xac nhan thong tin cuoi cung.

### 9.1. Muc dich cua man hinh nay
1. Giam rui ro ket noi sai mang.
2. Giam rui ro nhap sai password.
3. Cho phep chinh tay truoc khi thuc hien hanh dong that.
4. Hien thi them confidence hoac recommendation de ban quyet dinh.

### 9.2. Nhung thanh phan thuong co
1. Wi-Fi info card.
2. Edit form cho SSID, password va security.
3. Confidence chip hoac do tu tin.
4. Status message thong bao loi, loading, goi y hoac thanh cong.
5. Action buttons nhu Ket noi, Luu lich su, OCR lai, review thu cong hoac sao chep mat khau.

### 9.3. Cach ra quyet dinh tai man hinh review
1. Neu SSID khop voi danh sach Wi-Fi xung quanh va password co ve hop ly, co the bam Ket noi.
2. Neu confidence thap, uu tien kiem tra lai OCR text hoac sua tay.
3. Neu AI suggestion cho thay du lieu co van de, nen review thu cong truoc khi ket noi.
4. Neu chua muon ket noi ngay, ban co the luu lich su roi quay lai sau.

## 10. Cach ket noi Wi-Fi tu ben trong app
Sau khi thong tin da duoc xac nhan, nguoi dung co the bam Ket noi ngay.

### 10.1. Dieu gi xay ra khi bam Ket noi
1. App tao yeu cau ket noi den mang Wi-Fi da chon.
2. Android co the hien thong bao he thong hoac hop thoai xac nhan ket noi.
3. App cho ket qua thanh cong, that bai hoac timeout tuy theo tinh trang mang.
4. Neu luong luu lich su dang bat, du lieu mang co the duoc luu local sau khi ket noi thanh cong.

### 10.2. Neu ket noi thanh cong
1. App cap nhat trang thai thanh cong.
2. Mang co the xuat hien trong lich su ket noi.
3. Ban co the mo man hinh chi tiet mang de xem them telemetry neu may dang thuc su noi vao mang do.

### 10.3. Neu ket noi that bai
Hay kiem tra theo thu tu sau:

1. Password co dung khong.
2. SSID co dung khong.
3. Mang co dang phat khong.
4. Ban da cap quyen Wi-Fi, Location hoac Nearby Wi-Fi chua.
5. Ban co dang o qua xa access point khong.
6. Mang co yeu cau dang nhap portal sau khi ket noi khong.

## 11. Cach luu lich su va dung lai du lieu da luu
Ung dung co kha nang luu lich su ket noi hoac xu ly Wi-Fi local de ban dung lai sau nay.

### 11.1. Luu lich su de lam gi
1. Mo lai SSID da xu ly truoc do.
2. Xem chi tiet ket noi gan day.
3. Tiet kiem thoi gian khi can ket noi lai.
4. Ho tro demo luong san pham tu scan den save.

### 11.2. Cach vao Lich su
1. Tu Home, mo khu recent history de vao nhanh.
2. Hoac bam tab Lich su tren bottom navigation.

### 11.3. Nhung gi co trong man hinh History
1. Danh sach cac mang da luu.
2. Empty state neu chua co du lieu.
3. Cac bo loc nhu Tat ca, Bao mat, Cong cong tuy theo ban build.
4. Analytics card 30 ngay neu build dang hien thanh phan nay.

### 11.4. Cach dung du lieu lich su
1. Chon mot item de mo man hinh chi tiet mang.
2. Tiep tuc ket noi lai neu mang van con ton tai.
3. Xoa mang khong con can khoi lich su.

## 12. Man hinh Network Detail va cach doc thong tin mang
Khi ban chon mot mang tu Home hoac History, app mo man hinh chi tiet.

### 12.1. Nhung truong thong tin co the duoc hien thi
1. SSID.
2. Lan ket noi gan nhat.
3. Giao thuc bao mat.
4. Tan so mang.
5. Chat luong song.
6. Muc song dBm.
7. Usage chart.
8. Link speed, RX, TX neu may dang thuc su ket noi vao mang do.

### 12.2. Cac hanh dong co the thuc hien
1. Ket noi ngay lai.
2. Xoa mang nay khoi lich su local.

### 12.3. Khi nao telemetry hien thi chinh xac nhat
1. Khi may dang ket noi vao dung mang dang xem.
2. Khi Wi-Fi dang on dinh.
3. Khi app duoc phep doc thong tin ket noi hien tai.

## 13. Cach dung tinh nang Chia se Wi-Fi cho thiet bi o gan
Tinh nang nay huong toi ngu canh mot thiet bi da biet thong tin Wi-Fi va muon ho tro thiet bi khac tiep can nhanh hon.

### 13.1. Cach mo tinh nang
1. Tu Home hoac bottom navigation, vao tab Chia se.
2. App mo man hinh Share WiFi voi radar tim thiet bi o gan.

### 13.2. Luong su dung co ban
1. Bat man hinh Chia se tren thiet bi gui.
2. Cho app quet cac thiet bi lan can.
3. Chon thiet bi muc tieu trong danh sach neu app hien thi.
4. Bam Chia se neu ban la ben gui.
5. Ben nhan bam Chap nhan neu co yeu cau xac nhan.

### 13.3. Luu y khi su dung tinh nang chia se
1. Hai thiet bi nen o gan nhau.
2. Tinh nang nay phu thuoc vao ban build va cach mo phong chia se cua du an.
3. Neu khong tim thay thiet bi, hay kiem tra quyen, khoang cach va trang thai ket noi cua hai may.

## 14. Cach dung man hinh Cai dat
Settings la noi de dieu chinh cac tuy chon chung cua app.

### 14.1. Cac noi dung thuong co trong Cai dat
1. Dark mode toggle hoac tuy chon giao dien sang toi.
2. Thong tin app.
3. Link chinh sach bao mat hoac thong tin ho tro neu build da them vao.

### 14.2. Khi nao nen vao Cai dat
1. Khi muon doi theme sang toi hoac sang.
2. Khi can kiem tra thong tin ve app.
3. Khi muon tim link Privacy Policy hoac thong tin ho tro.

## 15. Cac tinh huong su dung mau

### 15.1. Tinh huong 1: Di cafe va quan co ma QR Wi-Fi
1. Mo app.
2. Chon Quet ma QR.
3. Dua camera vao ma QR tren ban hoac tren menu.
4. Kiem tra lai SSID va password.
5. Bam Ket noi.
6. Sau khi vao mang, luu lich su de lan sau mo lai nhanh hon.

### 15.2. Tinh huong 2: Wi-Fi duoc in tren bang thong bao
1. Mo app.
2. Chon Quet anh OCR.
3. Chup anh bang thong bao co ten mang va password.
4. Sua text OCR neu can.
5. Doi chieu voi goi y Wi-Fi xung quanh.
6. Bam Ket noi hoac Luu lich su.

### 15.3. Tinh huong 3: Ban da biet mat khau
1. Mo app.
2. Chon Nhap tay.
3. Dien SSID, password va security.
4. Review lai thong tin.
5. Bam Ket noi.

### 15.4. Tinh huong 4: Muon dung lai mang da ket noi hom truoc
1. Mo tab Lich su.
2. Chon mang da luu.
3. Xem Network Detail.
4. Bam Ket noi ngay.

## 16. Loi thuong gap va cach xu ly

### 16.1. App khong quet duoc QR
1. Kiem tra anh sang.
2. Dua ma QR vao giua khung scan.
3. Lam sach ong kinh camera.
4. Thu lai voi khoang cach khac.
5. Neu van that bai, nhap tay hoac dung OCR neu co anh.

### 16.2. OCR doc sai SSID hoac password
1. Sua truc tiep trong OCR text editor.
2. Sua trong form review.
3. Chup lai anh ro hon.
4. So sanh voi Wi-Fi xung quanh de chon dung SSID.

### 16.3. Khong thay Wi-Fi xung quanh
1. Kiem tra quyen Location hoac Nearby Wi-Fi.
2. Bat Wi-Fi tren may.
3. Dam bao may dang o trong vung co song Wi-Fi.

### 16.4. Bam Ket noi nhung khong vao duoc mang
1. Kiem tra lai password.
2. Kiem tra kieu bao mat.
3. Kiem tra mang co con phat hay khong.
4. Thu ket noi lai tu Network Detail hoac tu review.

### 16.5. Khong luu duoc du lieu hoac khong thay lich su
1. Kiem tra app da thuc hien xong buoc luu chua.
2. Mo lai tab Lich su de refresh.
3. Thu hoan tat mot luong scan hoac connect khac de tao du lieu moi.

### 16.6. Tinh nang Chia se khong tim thay thiet bi
1. Dat hai thiet bi gan nhau hon.
2. Kiem tra xem ca hai ben da vao dung man hinh chia se chua.
3. Kiem tra quyen va trang thai ket noi lien quan.

## 17. Cac meo de dung ung dung hieu qua hon
1. Uu tien QR neu dia diem co san ma, vi day la cach nhanh va it sai nhat.
2. Neu dung OCR, hay sua text OCR goc truoc khi ket noi.
3. Doi chieu ket qua OCR voi danh sach Wi-Fi xung quanh de tranh nham SSID.
4. Luu lich su cho cac mang hay dung de tiet kiem thoi gian lan sau.
5. Dung man hinh chi tiet mang de kiem tra lai chat luong song khi can demo hoac troubleshooting.

## 18. Ghi chu ve bao mat va quyen rieng tu
1. App duoc thiet ke de ho tro ket noi Wi-Fi hop le, khong phuc vu crack hay bypass bao mat.
2. Lich su ket noi duoc luu local de ho tro su dung lai nhanh hon.
3. Neu build co backend, mot phan du lieu OCR, SSID hoac password co the duoc gui len server de parse, validate hoac save network tuy theo cau hinh he thong.
4. Nguoi dung nen doc ky Privacy Policy neu ban phat hanh da bo sung tai man hinh Cai dat hoac landing page.

## 19. Quy trinh de xuat cho nguoi dung moi
Neu day la lan dau ban dung app, quy trinh don gian nhat la:

1. Mo app va hoan tat Onboarding.
2. Cap quyen Camera khi can.
3. Thu Quet ma QR truoc neu dia diem co ma QR.
4. Neu khong co QR, chuyen sang Quet anh OCR.
5. Neu OCR van khong du ro, dung Nhap tay.
6. Luon review lai SSID va password truoc khi bam Ket noi.
7. Luu lich su de nhung lan sau thao tac nhanh hon.

Tai lieu nay duoc viet theo luong su dung thuc te cua SmartWiFi-Connect hien co trong repo, nham giup nguoi dung cuoi, nguoi demo san pham va nguoi test app co mot huong dan day du tu lan mo dau tien den khi ket noi, luu va tai su dung Wi-Fi.