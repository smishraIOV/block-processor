package co.rsk.tools.processor.examples;
/**************************************************************************
 * This class dumps the balance of the Bridge constract at different points
 * in time. This is the number  of bitcoins the bridge has detected has been
 * pegged-in. Although this should match the amount of bitcoins transferred
 * to the Powpeg in Bitcoin transactions, the two measures could diverge
 * if there was a bug in RSK code or there are invalid pwg-in transactions.
 * By SDL.
 */

import co.rsk.core.Coin;
import co.rsk.tools.processor.RskBlockProcessor;
import co.rsk.tools.processor.RskProvider;
import org.ethereum.core.Transaction;

import java.io.File;
import java.math.BigDecimal;
import org.ethereum.vm.PrecompiledContracts;
import java.io.FileWriter;   // Import the FileWriter class
import java.io.IOException;  // Import the IOException class to handle errors

public class PowpegBalanceAnalyzer  extends RskBlockProcessor {

    RskProvider provider;

    File file;
    FileWriter fileWriter;

    public void begin() {
        createOutputFile();
    }

    public void createOutputFile() {
        try {
             file = new File("peg-balance.csv");
            if (file.createNewFile()) {
                System.out.println("File created: " + file.getName());
            } else {
                System.out.println("File already exists.");
            }
            fileWriter = new FileWriter(file);
            fileWriter.write("BlockNumber,UnixTime,PegFunds\n");

        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }

    public PowpegBalanceAnalyzer(RskProvider provider) {
        this.provider = provider;
    }

    public BigDecimal getBridgeLockedRBTC() {
        Coin balance = provider.getBalance(PrecompiledContracts.BRIDGE_ADDR);
        return RskProvider.toBTC(provider.TOTAL_SUPPLY.subtract(balance));
    }
    public BigDecimal getBridgeBalanceRBTC() {
        Coin balance = provider.getBalance(PrecompiledContracts.BRIDGE_ADDR);
        return RskProvider.toBTC(balance);
    }

    @Override
    public boolean processBlock() {
        String line = ""+currentBlock.getNumber()+","+
                currentBlock.getTimestamp()+","+
                getBridgeLockedRBTC().toPlainString();

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
        int wasabi100 = 1591000; // 2019/07/31 21:35:22 -03:00
        // good: 2_591_000; (255)
        int minBlock = 10_000; //1_000_001;
        int maxBlock = maxBlockchainBlock; //3200_000;
        int step = 10_000;
        RskProvider provider = new RskProvider(args,minBlock,maxBlock,step);
        PowpegBalanceAnalyzer analyzer = new PowpegBalanceAnalyzer(provider);
        provider.processBlockchain(analyzer);

    }
}
