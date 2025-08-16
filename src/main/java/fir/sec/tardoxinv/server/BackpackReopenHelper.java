package fir.sec.tardoxinv.server;

import fir.sec.tardoxinv.capability.PlayerEquipment;
import fir.sec.tardoxinv.network.SyncEquipmentPacketHandler;
import net.minecraft.server.level.ServerPlayer;

/** 배낭 착탈/리사이즈 후: 닫고 → 싱크 → 다시 열기 */
public final class BackpackReopenHelper {
    private BackpackReopenHelper() {}

    public static void onBackpackChanged(ServerPlayer sp, PlayerEquipment eq) {
        sp.closeContainer(); // 1) 닫기
        SyncEquipmentPacketHandler.syncToClient(sp, eq); // 2) 전체 스냅샷 싱크
        SyncEquipmentPacketHandler.openEquipmentScreen(sp, eq.getBackpackWidth(), eq.getBackpackHeight()); // 3) 재오픈
    }
}
