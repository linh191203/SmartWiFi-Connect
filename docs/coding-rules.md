# Coding Rules

## Quy tắc chung
- Mỗi màn hình 1 file chính
- Mỗi feature có package riêng
- Không nhét nhiều màn vào 1 file
- Không viết business logic trong composable
- Không hardcode string quá nhiều
- UI state phải rõ ràng

## Quy tắc đặt tên
- Screen: HomeScreen
- UiState: HomeUiState
- Preview data: HomePreviewData
- ViewModel: HomeViewModel
- Reusable component: PrimaryButton

## Quy tắc Compose
- Ưu tiên stateless UI
- Tách component khi block lặp lại hoặc dài quá
- Mỗi screen cần có preview
- Không viết composable 300 dòng nếu có thể tách

## Quy tắc chống miên man
- Mỗi buổi chỉ chọn 1 screen hoặc 1 module
- Không đổi design system giữa chừng
- Không refactor toàn project khi chưa xong luồng chính