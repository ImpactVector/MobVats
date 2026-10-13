package net.impactvector.mobvats.gamecontent.multiblock.mixer;

/** Motor contract shared by providers coupled inside the Mixer.
 * A provider reports normalized available drive; processing retains its normal cadence.
 */
public interface MixerDrive {
    double availableDrive(MultiblockMixer mixer);
    /** Pays for one full drive tick. Queries never consume resources. */
    default boolean driveTick(MultiblockMixer mixer) {
        return availableDrive(mixer) >= 1.0;
    }
}
