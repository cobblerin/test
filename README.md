# Cobblemon 교배 시스템

Fabric 1.21.1 / Cobblemon 1.8.4 기준 클라이언트·서버 분리 빌드입니다.

## 설치

- 서버의 `mods` 폴더에는 `cobblemon-breeding-server-0.1.0.jar`를 넣습니다.
- 게임 클라이언트의 `mods` 폴더에는 `cobblemon-breeding-client-0.1.0.jar`를 넣습니다.
- 서버와 모든 접속 클라이언트에 해당 역할의 JAR이 각각 필요합니다.
- ZIP 파일에는 두 JAR이 모두 들어 있습니다.
- 목장에 영양제를 들고 우클릭하면 최대 64개까지 목장 보관함에 넣습니다.
- 목장 PC에서 `교배` 버튼은 보관된 영양제 1개로 교배를 시작하고, `자동 ON/OFF` 버튼은 알 수령 후 자동으로 다음 교배를 시작할지 설정합니다.
- 완성된 알은 파티의 첫 빈 슬롯에 먼저 들어가며, 파티가 가득 차면 PC의 첫 빈 슬롯을 사용합니다. 파티와 PC가 모두 가득 차면 알은 목장에 보관됩니다.
- 파티 1번 포켓몬의 불꽃몸 특성이 활성화되면 정상 이동으로 얻는 알걸음이 2배가 됩니다. `/알걸음 <슬롯>`에서 진행률과 불꽃몸 적용 여부를 확인할 수 있습니다.
- 종족별 알걸음은 기본적으로 Cobblemon의 `eggCycles × 256`을 사용합니다. 서버의 `config/cobblemon-breeding/egg-steps.json`에 `{"values":{"cobblemon:pikachu":2560}}` 형식으로 종족별 걸음 수를 지정할 수 있습니다.

최신 JAR은 저장소의 [Releases](https://github.com/cobblerin/test/releases) 또는 저장소 파일 목록에서 받을 수 있습니다.
