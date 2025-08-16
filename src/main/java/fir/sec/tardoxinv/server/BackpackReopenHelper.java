package fir.sec.tardoxinv.server;

import fir.sec.tardoxinv.capability.PlayerEquipment;
import fir.sec.tardoxinv.network.SyncEquipmentPacketHandler;
import net.minecraft.server.level.ServerPlayer;

/** 배낭 착탈/리사이즈 이후 재오픈 표준 루트 */
public final class BackpackReopenHelper {
    private BackpackReopenHelper() {}

    public static void onBackpackChanged(ServerPlayer sp, PlayerEquipment eq) {
        // 1) 컨테이너 닫기
        sp.closeContainer();

        // 2) 상태 스냅샷 동기화(장착/배낭/바인딩/게이머룰)
        SyncEquipmentPacketHandler.sync(sp, eq);

        // 3) 새 크기로 장비 화면 다시 열기
        SyncEquipmentPacketHandler.openEquipmentScreen(sp, eq.getBackpackWidth(), eq.getBackpackHeight());
    }
}
