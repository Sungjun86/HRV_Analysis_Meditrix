# HRV_Featrue (Android / Kotlin)

버튼을 누르면 CSV 파일을 선택하고, CSV의 숫자 데이터를 라인 그래프로 출력하는 Android Studio 프로젝트입니다.

## 주요 기능
1. `CSV 파일 불러오기` 버튼 클릭
2. 시스템 파일 선택기에서 CSV 선택
3. 각 행에서 숫자로 변환 가능한 첫 번째 값을 추출
4. MPAndroidChart `LineChart`로 그래프 출력
5. 하단에 읽은 총 행 수 / 그래프에 사용된 숫자 개수 표시

## Android Studio Koala 에러 대응
아래 에러가 나는 경우:

`An exception occurred applying plugin request [id: 'com.android.application']`
`Failed to apply plugin 'com.android.internal.version-check'`

다음을 확인하세요.

- **Gradle JDK를 17 또는 21로 설정**
  - Android Studio > Settings > Build, Execution, Deployment > Build Tools > Gradle > **Gradle JDK**
- 이 프로젝트는 Koala 호환을 위해 다음 조합으로 맞춰두었습니다.
  - Android Gradle Plugin: `8.7.3`
  - Gradle Wrapper: `8.9`
  - Kotlin: `2.0.21`

## 실행 방법
1. Android Studio에서 프로젝트 열기
2. Gradle Sync
3. 에뮬레이터/실기기 실행
