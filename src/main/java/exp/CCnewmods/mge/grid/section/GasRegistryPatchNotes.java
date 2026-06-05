package exp.CCnewmods.mge.grid.section;

/**
 * Patch instructions for GasRegistry.java — add these two methods to the
 * existing GasRegistry class (they cannot live here due to package structure).
 *
 * ── PASTE INTO GasRegistry.java ───────────────────────────────────────────────
 *
 * In the BY_ID / ALL_GASES field block, add:
 *   private static final Map<String, Integer> ORDINALS = new LinkedHashMap<>();
 *
 * In the register() method, after adding to ALL_GASES, add:
 *   ORDINALS.put(id.toString(), ALL_GASES.size() - 1);
 *
 * Then add these two public static methods at the end of GasRegistry:
 *
 * public static int ordinalOf(Gas gas) {
 *     return ORDINALS.getOrDefault(gas.id().toString(), -1);
 * }
 *
 * public static @Nullable Gas byOrdinal(int ordinal) {
 *     if (ordinal < 0 || ordinal >= ALL_GASES.size()) return null;
 *     return ALL_GASES.get(ordinal);
 * }
 *
 * public static Map<Gas, Float> getOrDefault(Gas gas) {
 *     // Already exists as standardAtmosphere()
 * }
 *
 * The changes are minimal — two new fields and two new methods.
 * ──────────────────────────────────────────────────────────────────────────────
 *
 * This file exists only to document the patch and will be deleted once
 * GasRegistry.java has been updated directly.
 */
final class GasRegistryPatchNotes {
    private GasRegistryPatchNotes() {}
}
