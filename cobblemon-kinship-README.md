# Cobblemon 인연 시스템

Minecraft 1.21.1 / Cobblemon 1.8.4 / Fabric 기준으로 만드는 서버 중심 인연 시스템입니다.

## 배포 JAR

분리 배포본은 다음과 같습니다.

- 서버: `build/libs/cobblemon-kinship-server-0.1.0.jar`를 전용 서버의 `mods/`에 넣습니다.
- 클라이언트: `build/libs/cobblemon-kinship-client-0.1.0.jar`를 플레이어 클라이언트의 `mods/`에 넣습니다.

서버 JAR은 서버 엔트리포인트와 Cobblemon 저장소·보상 처리를 포함하고, 클라이언트 JAR은 HUD·입력·언어 리소스만 포함합니다. 두 JAR은 같은 모드 ID와 버전을 사용하므로 양쪽에 함께 설치해 연결합니다.

게임 규칙은 Minecraft API와 분리된 코어 모듈로 동작하며, Fabric 서버의 Cobblemon 1.8.4 파티·PC 어댑터와 커스텀 HUD까지 연결되어 있습니다. 코어가 납품 판정, 별 계산, 포인트 지급, 누적 보상 해금을 서버 권위로 처리합니다.

플레이어 진행도는 `PlayerProgressStore` 인터페이스 뒤에 분리되어 있습니다. 현재 테스트에서는 메모리 저장소를 사용하며, 실제 서버에서는 이 어댑터를 `PersistentState` 또는 서버 JSON 저장소로 교체합니다.

현재는 재시작 복구를 검증할 수 있는 `FilePlayerProgressStore`도 포함되어 있습니다. 저장 파일은 버전과 거래 이력을 함께 기록하고, 임시 파일을 원본으로 교체하는 방식으로 저장합니다.
저장 파일이 손상되었을 때는 원본을 `.corrupt-*` 백업으로 격리한 뒤 새 진행도로 서버를 계속 기동합니다.

Fabric 네트워크에는 진행도 조회, 후보 조회, 미리보기, 납품 확정, 보상 수령 payload가 연결되어 있습니다. 클라이언트의 `J` 키 입력이 서버에 조회를 요청하고, 서버가 만든 JSON 스냅샷을 받아 HUD에 별과 포인트를 표시합니다.

현재 클라이언트에는 스크롤 가능한 전체 화면형 인연 목록과 상세 납품 화면이 포함되어 있습니다. 각 인연의 완료 별 수, 조건별 진행도, 누적 보상 상태를 서버 응답으로 표시합니다.

목록에서 인연을 클릭하면 상세 화면으로 이동하고, 서버가 보낸 납품 후보를 선택할 수 있습니다. 후보 선택 후 1~4키로 IV 단계를 고르고 Enter로 미리보기와 최종 납품을 진행합니다.

대기 보상은 목록 화면에서 `R`키로 수령 요청을 보낼 수 있습니다. 현재 기본 런타임은 서버 파일 보관함에 수령 이력을 먼저 기록하며, 같은 보상 요청은 멱등적으로 처리합니다.

서버 시작 시 월드의 `data/cobblemon_kinship/kinships.json`과 `data/cobblemon_kinship/rewards.json`을 우선 읽습니다. 파일이 없으면 모드 내장 기본 설정을 해당 위치에 자동 복사하므로, 첫 실행 후 파일을 직접 수정할 수 있습니다. 설정을 읽지 못하면 기본 내장 설정으로 안전하게 시작합니다.
두 설정 파일은 현재 `version: 1` 형식을 사용하며, 지원하지 않는 버전은 자동으로 기본 설정으로 fallback합니다.

실제 치장 아이템 전달 어댑터가 연결되기 전에는 보관함이 보상 ID를 안전하게 보존합니다. 이후 치장 시스템을 확정하면 `RewardDelivery` 구현만 교체하면 됩니다.
Fabric 런타임도 `KinshipRuntime.start(..., RewardDelivery)` 확장 진입점을 제공하므로, 운영 서버의 치장/아이템 시스템을 시작 시 주입할 수 있습니다.
`rewards.json`의 `items`에 `{ "보상ID": { "item": "minecraft:diamond", "count": 1 } }`를 지정하면 빈 인벤토리 슬롯에 vanilla 아이템을 우선 지급하고, 지급할 수 없으면 파일 보관함으로 넘깁니다.

## 검증

```bash
rm -rf build/classes
mkdir -p build/classes
java --add-modules jdk.compiler -m jdk.compiler/com.sun.tools.javac.Main \
  -d build/classes \
  $(find src/main/java/com/cobblemon/kinship -maxdepth 1 -name '*.java') \
  src/test/java/com/cobblemon/kinship/KinshipServiceSelfTest.java
java -cp build/classes com.cobblemon.kinship.KinshipServiceSelfTest
```

## 규칙

- 레벨 납품은 해당 레벨 이하의 미완료 레벨 조건을 모두 적용합니다.
- IV 조건은 요청으로 선택한 한 단계만 적용합니다.
- 이로치는 레벨·IV 납품과 동시에 적용됩니다.
- 포켓몬은 소유 저장소에서 서버가 검증한 뒤 소비됩니다.
- 저장 실패 시 진행도 체크포인트와 복구 가능한 저장소의 포켓몬을 되돌립니다.
- 별은 인연에 포함된 모든 종이 같은 조건을 완료했을 때만 생성됩니다.
- 포인트와 보상은 새로 생성된 별에 대해서만 한 번 지급됩니다.

