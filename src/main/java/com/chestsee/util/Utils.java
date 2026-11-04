package com.chestsee.util;

import com.chestsee.ChestSee;
import com.chestsee.log.LogMaintenance;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class Utils
{
    public static String getTimeStamp()
    {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM.yy  HH:mm:ss");
        return String.format("[%s]", dtf.format(LocalDateTime.now()));
    }

    public static String getDimString(Holder<DimensionType> dimensionTypeHolder)
    {
        String dimStr = "unkown";
        if(dimensionTypeHolder.is(BuiltinDimensionTypes.OVERWORLD))
            dimStr = "Overworld";
        else if(dimensionTypeHolder.is(BuiltinDimensionTypes.NETHER))
            dimStr = "The Nether";
        else if(dimensionTypeHolder.is(BuiltinDimensionTypes.END))
            dimStr = "The End";
        return dimStr;
    }

    public static ChestBlockEntity getSecondChest(BlockState blockState, BlockEntity blockEntity, Level world)
    {
        if (!(blockEntity instanceof ChestBlockEntity)) return null;
        Direction facingDirection = blockState.getValue(BlockStateProperties.HORIZONTAL_FACING);

        ChestType chestType = blockState.getValue(ChestBlock.TYPE);
        switch (chestType)
        {
            case LEFT:
                if (facingDirection == Direction.EAST) return (ChestBlockEntity) world.getBlockEntity(blockEntity.getBlockPos().south());
                else if (facingDirection == Direction.SOUTH) return (ChestBlockEntity) world.getBlockEntity(blockEntity.getBlockPos().west());
                else if (facingDirection == Direction.WEST) return (ChestBlockEntity) world.getBlockEntity(blockEntity.getBlockPos().north());
                else return (ChestBlockEntity) world.getBlockEntity(blockEntity.getBlockPos().east());
            case RIGHT:
                if (facingDirection == Direction.EAST) return (ChestBlockEntity) world.getBlockEntity(blockEntity.getBlockPos().north());
                else if (facingDirection == Direction.SOUTH) return (ChestBlockEntity) world.getBlockEntity(blockEntity.getBlockPos().east());
                else if (facingDirection == Direction.WEST) return (ChestBlockEntity) world.getBlockEntity(blockEntity.getBlockPos().south());
                else return (ChestBlockEntity) world.getBlockEntity(blockEntity.getBlockPos().west());
            default:
                return null;
        }
    }

    public static List<ItemStack> getItems(Container container)
    {
        List<ItemStack> itemStacks = new ArrayList<>();

        for(int i = 0; i < container.getContainerSize(); i++)
        {
            ItemStack itemStack = container.getItem(i);
            if(!itemStack.isEmpty())
            {
                itemStacks.add(itemStack);
            }
        }
        return itemStacks;
    }

    public static void writeLog(String timeStamp, String playerName, String path, List<String> itemsAdded, List<String> itemsRemoved)
    {
        try
        {
            String logStr;

            logStr = timeStamp + " " + playerName + "\nItems Added: " + itemsAdded + "\nItems Removed: " + itemsRemoved + "\n\n";

            append(path, logStr);
        } catch (IOException e)
        {
            ChestSee.LOGGER.error("Error writing log");
            e.printStackTrace();
        }
    }

    public static void writeMessageLog(String timeStamp, String playerName, String path, String message)
    {
        try
        {
            String logStr;

            logStr = timeStamp + " " + playerName + "\n" + message + "\n\n";

            append(path, logStr);
        } catch (IOException e) {
            ChestSee.LOGGER.error("Error writing log");
            e.printStackTrace();
        }
    }

    private static void append(String path, String logStr) throws IOException
    {
        File file = new File(path + ".txt");
        try(FileWriter fileWriter = new FileWriter(file, true))
        {
            fileWriter.write(logStr);
        }

        int maxKb = ChestSee.getConfig().maxFileSizeKb;
        if(maxKb > 0)
            LogMaintenance.trimToSize(file.toPath(), maxKb * 1024L);
    }

    public static List<String> itemStackListToStrList(List<ItemStack> items)
    {
        String itemsStr = items.toString();
        String[] itemsList = itemsStr.substring(1, itemsStr.length() - 1).split(",");
        List<String> outputList = new ArrayList<>();

        for (String item : itemsList)
        {
            if(item.isEmpty()) continue;

            String[] parts = item.trim().split(" ");
            if(parts.length < 2) continue;

            String countStr = parts[0];
            String type = parts[1];

            outputList.add(countStr + " " + type);
        }
        return outputList;
    }

    public static List<String> getChangedItems(List<String> list1, List<String> list2)
    {
        Map<String, Integer> countMap1 = new HashMap<>();
        Map<String, Integer> countMap2 = new HashMap<>();

        for (String item : list1)
        {
            String[] parts = item.split(" ", 2);
            int count = Integer.parseInt(parts[0]);
            String name = parts[1];
            countMap1.put(name, countMap1.getOrDefault(name, 0) + count);
        }

        for (String item : list2)
        {
            String[] parts = item.split(" ", 2);
            int count = Integer.parseInt(parts[0]);
            String name = parts[1];
            countMap2.put(name, countMap2.getOrDefault(name, 0) + count);
        }

        List<String> outputList = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : countMap2.entrySet())
        {
            String name = entry.getKey();
            int count2 = entry.getValue();
            int count1 = countMap1.getOrDefault(name, 0);
            int countDiff = count2 - count1;
            if (countDiff > 0) {
                outputList.add(countDiff + " " + name);
            }
        }

        return outputList;
    }
}
