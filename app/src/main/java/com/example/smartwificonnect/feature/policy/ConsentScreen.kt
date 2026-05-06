package com.example.smartwificonnect.feature.policy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class ConsentSection(
    val title: String,
    val items: List<String>,
)

@Composable
fun ConsentScreen(
    onAcceptClick: () -> Unit,
    onDeclineClick: () -> Unit,
) {
    val sections = listOf(
        ConsentSection(
            title = "Ứng dụng này làm gì?",
            items = listOf(
                "Quét QR hoặc OCR để đọc thông tin Wi-Fi từ ảnh và camera.",
                "Kết nối vào mạng Wi-Fi phù hợp khi bạn chủ động xác nhận hoặc yêu cầu kết nối.",
                "Lưu lịch sử kết nối ngay trên thiết bị để bạn xem lại và chia sẻ nhanh hơn.",
            ),
        ),
        ConsentSection(
            title = "Những quyền có thể được xin khi sử dụng",
            items = listOf(
                "Camera: dùng để quét QR và chụp ảnh OCR.",
                "Vị trí và Nearby Wi-Fi: Android yêu cầu để quét các mạng Wi-Fi ở gần.",
                "Trạng thái và thay đổi mạng Wi-Fi: dùng để đọc trạng thái và thực hiện kết nối.",
            ),
        ),
        ConsentSection(
            title = "Dữ liệu được xử lý như thế nào?",
            items = listOf(
                "Thông tin như OCR text, SSID, mật khẩu và lịch sử kết nối có thể được xử lý cục bộ trên máy.",
                "Nếu bạn bật backend AI hoặc bản phát hành production có dùng server, OCR text, SSID hoặc mật khẩu có thể được gửi lên server để phân tích, xác thực hoặc lưu mạng.",
                "Bản phát hành chính thức cần có đường dẫn Privacy Policy công khai trong phần Cài đặt hoặc trang phát hành.",
            ),
        ),
        ConsentSection(
            title = "Cam kết hiển thị minh bạch",
            items = listOf(
                "App sẽ chỉ xin quyền vào đúng thời điểm bạn dùng tính năng liên quan.",
                "Nếu bạn từ chối chính sách này, app sẽ không tiếp tục vào luồng sử dụng chính.",
            ),
        ),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFF6F8FC), Color(0xFFEAF0FA)),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 20.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = Color.White,
                shadowElevation = 10.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
                ) {
                    Text(
                        text = "Điều khoản sử dụng và quyền riêng tư",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF19202C),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Trước khi vào app, vui lòng đọc và xác nhận cách SmartWiFi-Connect xử lý quyền truy cập, quét Wi-Fi và dữ liệu liên quan.",
                        color = Color(0xFF697386),
                        fontSize = 15.sp,
                        lineHeight = 23.sp,
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    sections.forEachIndexed { index, section ->
                        ConsentSectionCard(section = section)
                        if (index != sections.lastIndex) {
                            Spacer(modifier = Modifier.height(14.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = "Bằng việc chọn Đồng ý và tiếp tục, bạn xác nhận đã đọc phần giải thích này và chấp nhận cho app hiển thị các hộp thoại quyền cần thiết khi sử dụng từng tính năng.",
                        color = Color(0xFF3D4657),
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        textAlign = TextAlign.Start,
                    )

                    Spacer(modifier = Modifier.height(22.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = onDeclineClick,
                            modifier = Modifier
                                .weight(1f)
                                .height(58.dp),
                            shape = RoundedCornerShape(15.dp),
                        ) {
                            Text(
                                text = "Từ chối",
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                        Button(
                            onClick = onAcceptClick,
                            modifier = Modifier
                                .weight(1f)
                                .height(58.dp),
                            shape = RoundedCornerShape(15.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4253F0),
                            ),
                        ) {
                            Text(
                                text = "Đồng ý và tiếp tục",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsentSectionCard(section: ConsentSection) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFFF8FAFF),
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = section.title,
                color = Color(0xFF1C2535),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
            )
            section.items.forEach { item ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 7.dp)
                            .size(7.dp)
                            .background(Color(0xFF4A59F2), CircleShape),
                    )
                    Text(
                        text = item,
                        color = Color(0xFF5E687B),
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
