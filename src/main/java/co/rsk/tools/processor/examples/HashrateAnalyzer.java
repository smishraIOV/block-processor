package co.rsk.tools.processor.examples;

import co.rsk.core.BlockDifficulty;
import co.rsk.core.Coin;
import co.rsk.tools.processor.RskBlockProcessor;
import co.rsk.tools.processor.RskProvider;
import org.ethereum.core.Block;
import org.ethereum.vm.PrecompiledContracts;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

/********************************************************************
 * This class dumps the RSK hashrate at several points in history.
 * The computation is done.
 * By SDL.
 ********************************************************************/
public class HashrateAnalyzer extends RskBlockProcessor {

    RskProvider provider;

    File file;
    FileWriter fileWriter;

    public void begin() {
        createOutputFile();
    }

    public void createOutputFile() {
        try {
            file = new File("hashrate.csv");
            if (file.createNewFile()) {
                System.out.println("File created: " + file.getName());
            } else {
                System.out.println("File already exists.");
            }
            fileWriter = new FileWriter(file);
            fileWriter.write("BlockNumber,UnixTime,Difficulty [EH],Hashrate[EH/s]\n");

        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }

    public HashrateAnalyzer(RskProvider provider) {
        this.provider = provider;
    }


    final BigInteger tera = BigInteger.valueOf(1_000_000_000_000L);
    final BigInteger giga = BigInteger.valueOf(1_000_000_000L);
    final BigInteger exa = giga.multiply(giga);

    static int n= 100; // block window to compute average

    @Override
    public boolean processBlock() {
        BlockDifficulty difficulty = currentBlock.getDifficulty();

        //Block parentBlock = ctx.getBlockStore().getBlockByHash(currentBlock.getParentHash().getBytes());
        // To compute the hasrate, we assume the difficuly is constant
        // over the last n blocks, and we get the average block rate.
        Block p100Block = ctx.getBlockStore().getChainBlockByNumber(currentBlock.getNumber()-n);
        BlockDifficulty cumulativeDifficulty = ctx.getBlockStore().getTotalDifficultyForHash(currentBlock.getHash().getBytes());
        BlockDifficulty p100cumulativeDifficulty = ctx.getBlockStore().getTotalDifficultyForHash(p100Block.getHash().getBytes());
        BigInteger difDifficulty = cumulativeDifficulty.subtract(p100cumulativeDifficulty).asBigInteger();
        long deltaTime = currentBlock.getTimestamp()-p100Block.getTimestamp();
        double avgTimeBetweenBlocks = deltaTime * 1.0 / n;

        // This would be the computation if we were using floating point arithmetic
        // double hashesPerSecond = diffiulty / avgTimeBetweenBlocks;

        // By expanding terms we can use only  integers
        // hashesPerSecond = difficulty * n / deltaTime
        //
        BigInteger hashesPerSecond = difDifficulty.
                divide(BigInteger.valueOf(deltaTime)).divide(exa);

        // hashesPerSecond_nouncles:
        // This measure of hashes per second does not take into consideration
        // the work provided by uncles and therefore it is not accurate.
        BigInteger hashesPerSecond_nouncles = difficulty.asBigInteger().
                multiply(BigInteger.valueOf(100)).
                divide(BigInteger.valueOf(deltaTime)).divide(exa);

        BigInteger difficultyExas = difficulty.asBigInteger().divide(exa);
        String line = ""+currentBlock.getNumber()+","+
                currentBlock.getTimestamp()+","+
                difficultyExas.toString()+","+
                hashesPerSecond.toString();

        System.out.println(line);
        try {
            fileWriter.write(line+"\n");
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public void end() {
        close();
    }
    public void close() {
        try {
            fileWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main (String args[]) {
        int maxBlockchainBlock = 3_220_000; // 3219985
        int minBlock = 10_000; //1_000_001;
        int maxBlock = maxBlockchainBlock; //3200_000;
        int step = 10_000;
        RskProvider provider = new RskProvider(args,minBlock,maxBlock,step);
        HashrateAnalyzer analyzer = new HashrateAnalyzer(provider);
        provider.processBlockchain(analyzer);

    }
}
