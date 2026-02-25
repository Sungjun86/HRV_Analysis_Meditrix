# CSV Reader Android App (Kotlin)

버튼을 누르면 CSV 파일을 선택하고, ECG 데이터를 텍스트와 2개의 라인 차트로 표시하는 안드로이드 스튜디오 프로젝트입니다.

## 동작 방식
1. `CSV 파일 선택` 버튼 클릭
2. 시스템 파일 선택기에서 CSV 파일 선택
3. 선택된 파일을 줄 단위로 읽어 텍스트로 표시
4. 각 행에서 숫자로 변환 가능한 첫 번째 값을 ECG 값으로 추출
5. 1번 차트에 Raw ECG를 출력
6. Pan & Tompkins 처리 결과(전처리 + 파생 신호)를 2번 차트와 텍스트 요약으로 출력
7. `RR Interval CSV 저장` 버튼으로 RR interval 결과를 CSV 파일로 저장

## Pan & Tompkins 처리(샘플링 500Hz)
- Pre-Processing: Band-pass (HPF 5Hz + LPF 15Hz)
- Derivative Filter
- Squaring
- Moving Window Integration (150ms)
- Adaptive Threshold (SPKI/NPKI 기반) + Refractory period(200ms)

## RR Interval 저장 CSV 포맷
- 헤더: `index,rr_interval_samples,rr_interval_ms`
- 각 행: RR interval의 샘플 수 및 ms 변환값

## 출력 정보
- Raw ECG Line Chart
- Pan & Tompkins Processed Chart(적분 신호)
- Pan & Tompkins 텍스트 결과(Sampling rate, Threshold, R-peak 개수, RR interval, 추정 BPM, 피크 index)

## 실행 방법
1. Android Studio에서 이 폴더 열기
2. Gradle Sync 완료
3. 에뮬레이터/실기기에서 실행

## 참고
- 텍스트는 최대 150줄까지 미리보기로 표시합니다.
- 그래프는 각 행에서 찾은 첫 번째 숫자값을 사용합니다.
- BPM 계산은 샘플링 주파수 500Hz 가정 기반의 근사치입니다.
- 복잡한 CSV 규칙(따옴표 이스케이프 등)은 별도 처리하지 않습니다.
