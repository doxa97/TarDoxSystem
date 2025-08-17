package fir.sec.tardoxinv.client.ui;

import fir.sec.tardoxinv.menu.GridSlot;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** 서버 SyncUtilBindsPacket 수신 시 갱신되는 클라 캐시. */
public final class ClientHotbarBindings {
    private ClientHotbarBindings() {}

    private record Key(GridSlot.Storage st, int index) {
        @Override public int hashCode() { return Objects.hash(st, index); }
    }
    private static final Map<Key, Integer> bySlot = new HashMap<>();

    /** 예: hb=5..8, st=BASE/BACKPACK, index=그리드 인덱스 */
    public static void setBinding(int hb, GridSlot.Storage st, int index) {
        bySlot.put(new Key(st, index), hb);
    }

    public static void clearBinding(GridSlot.Storage st, int index) {
        bySlot.remove(new Key(st, index));
    }

    public static void clearAll() { bySlot.clear(); }

    /** 오버레이 숫자 조회 */
    public static Integer getNumberFor(GridSlot gs) {
        return bySlot.get(new Key(gs.getStorage(), gs.getGridIndex()));
    }
}
