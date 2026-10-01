package com.eliaslucky.ibm_mainframe.blocks;

import com.eliaslucky.ibm_mainframe.channel.ChannelCommand;
import com.eliaslucky.ibm_mainframe.channel.ChannelDevice;
import com.eliaslucky.ibm_mainframe.channel.ChannelResult;
import com.eliaslucky.ibm_mainframe.dataset.Dataset;
import com.eliaslucky.ibm_mainframe.items.DiskPackItem;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * The IBM 2311. Holds one disk pack; exposes its datasets to the
 * catalog through typed accessors rather than CCWs.
 *
 * <p>Unlike tapes, disk I/O in the mod is dataset-oriented - a
 * program opens a DSN, not a raw record stream. The kernel looks up
 * the DSN through the catalog, the catalog resolves it to a mounted
 * drive, and the drive's methods return or accept whole datasets.
 * That keeps a running FORTRAN program from having to issue CCWs
 * directly, and matches how real OS/360 presented DASD to programs.
 *
 * <p>The device still implements {@link ChannelDevice} so it appears
 * on the channel scan and gets a unit address
 */
public class DiskDriveBlockEntity extends BlockEntity implements ChannelDevice {
    private ItemStack pack = ItemStack.EMPTY;

    public DiskDriveBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DISK_DRIVE.get(), pos, state);
    }

    // --- Pack handling ---------------------------------------------------

    public boolean hasPack() { return !pack.isEmpty(); }

    public String volumeSerial() {
        return hasPack() ? DiskPackItem.getVolumeSerial(pack) : "";
    }

    public boolean insertPack(ItemStack stack) {
        if (hasPack()) return false;
        if (!(stack.getItem() instanceof DiskPackItem)) return false;
        this.pack = stack.copyWithCount(1);

        // Assign a serial on first mount if blank.
        if (DiskPackItem.getVolumeSerial(pack).isEmpty()) {
            String serial = "D" + Integer.toHexString(
                    (int) (System.currentTimeMillis() & 0xFFFFF)).toUpperCase();
            DiskPackItem.setVolumeSerial(pack, serial);
        }
        setChanged();
        return true;
    }

    public ItemStack ejectPack() {
        ItemStack out = pack;
        pack = ItemStack.EMPTY;
        setChanged();
        return out;
    }

    public String statusLine() {
        if (!hasPack()) return "2311: no pack mounted.";
        int n = DiskPackItem.getDatasetNames(pack).size();
        return "2311: " + volumeSerial() + " -- " + n + " dataset(s).";
    }

    // --- Dataset access (called by the catalog) --------------------------

    public List<String> datasetNames() {
        return hasPack() ? DiskPackItem.getDatasetNames(pack) : List.of();
    }

    public Dataset readDataset(String dsn) {
        return hasPack() ? DiskPackItem.readDataset(pack, dsn) : null;
    }

    public void writeDataset(Dataset ds) {
        if (!hasPack()) return;
        DiskPackItem.writeDataset(pack, ds);
        setChanged();
    }

    public boolean deleteDataset(String dsn) {
        if (!hasPack()) return false;
        boolean r = DiskPackItem.deleteDataset(pack, dsn);
        if (r) setChanged();
        return r;
    }

    // --- ChannelDevice ---------------------------------------------------

    @Override public String deviceName() { return "DISK"; }

    @Override
    public ChannelResult execute(ChannelCommand cmd) {
        // Disk access is dataset-oriented; CCW I/O is not used.
        return switch (cmd.op()) {
            case SENSE -> ChannelResult.read(new byte[] { 0 });
            case NOP   -> ChannelResult.OK;
            default    -> ChannelResult.REJECT;
        };
    }

    @Override public String deviceClass() { return "disk"; }
    @Override public String vendorId()    { return "ibm_mainframe"; }
    @Override public String productId()   { return "ibm_2311"; }
    @Override public String description() { return "IBM 2311 Disk Storage Drive"; }
    @Override public void write(byte[] d) {}
    @Override public byte[] read(int maxBytes) { return new byte[0]; }
    @Override public int ioctl(int cmd, byte[] arg) { return -1; }
    @Override public boolean isReady() { return hasPack(); }
    @Override public boolean hasData() { return false; }

    // --- NBT -------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!pack.isEmpty()) tag.put("Pack", pack.save(new CompoundTag()));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.pack = tag.contains("Pack")
                ? ItemStack.of(tag.getCompound("Pack"))
                : ItemStack.EMPTY;
    }
}
