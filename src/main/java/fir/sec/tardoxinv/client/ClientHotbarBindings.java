package fir.sec.tardoxinv.client;

import fir.sec.tardoxinv.menu.slot.GridSlot;

import java.util.HashMap;
import java.util.Map;

/** 서버→클라 바인딩 스냅샷 캐시 (오버레이 숫자 렌더용) */
public final class ClientHotbarBindings {
    private ClientHotbarBindings() {}

    /** (storageCode,index) → hotbarIndex */
    private static final Map<Long, Integer> SLOT_TO_HB = new HashMap<>();

    /** 전체 덮어쓰기(누락은 해제) */
    public static void setAll(byte[] storageByHb, int[] indexByHb) {
        SLOT_TO_HB.clear();
        if (storageByHb == null || indexByHb == null) return;
        int n = Math.min(storageByHb.length, indexByHb.length);
        for (int hb = 0; hb < n; hb++) {
            byte st = storageByHb[hb];
            int idx = indexByHb[hb];
            if (st == 0 || idx < 0) continue;
            SLOT_TO_HB.put(pack(st, idx), hb);
        }
    }

    /** 오버레이 숫자 조회 */
    public static Integer getNumberFor(GridSlot gs) {
        byte sc = (byte) (gs.getStorage() == GridSlot.Storage.BASE ? 1 : 2);
        return SLOT_TO_HB.get(pack(sc, gs.getIndex()));
    }

    private static long pack(byte st, int idx) {
        return ((long) st << 32) | (idx & 0xffffffffL);
    }
}
