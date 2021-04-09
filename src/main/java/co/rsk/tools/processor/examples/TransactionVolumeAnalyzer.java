package co.rsk.tools.processor.examples;

import co.rsk.tools.processor.RskBlockProcessor;
import co.rsk.tools.processor.RskProvider;
import org.ethereum.core.Block;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/*************************************************************************
 * This class analyzes the transaction volume per day at different points
 * in RSK history. It dumps two values: transactions/day including REMASC
 * transactions, and transactions/day excluding REMASC transactions.
 * By SDL.
 *************************************************************************/
public class TransactionVolumeAnalyzer  extends RskBlockProcessor  {

    RskProvider provider;

    File file;
    FileWriter fileWriter;

    public void begin() {
        createOutputFile();
    }

    public void createOutputFile() {
        try {
            file = new File("txvolume.csv");
            if (file.createNewFile()) {
                System.out.println("File created: " + file.getName());
            } else {
                System.out.println("File already exists.");
            }
            fileWriter = new FileWriter(file);
            fileWriter.write("BlockNumber,UnixTime,Txs/day,Txs/day (Excl. REMASC)\n");

        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }

    public TransactionVolumeAnalyzer(RskProvider provider) {
        this.provider = provider;
    }

    long dayStartTimeStamp =0;
    long dayInSeconds = 60*60*24;
    long acumTxs =0;
    long acumTxs_no_remasc =0;

    @Override
    public boolean processBlock() {

        if (dayStartTimeStamp==0)
            // First time initialization
            dayStartTimeStamp = currentBlock.getTimestamp();
        int txs = currentBlock.getTransactionsList().size();
        if (txs<=0)
            return false; // panic
        acumTxs +=txs;
        acumTxs_no_remasc +=(txs-1);

        if (currentBlock.getTimestamp()>dayStartTimeStamp+dayInSeconds) {
            dayStartTimeStamp = currentBlock.getTimestamp();
            String line = "" + currentBlock.getNumber() + "," +
                    currentBlock.getTimestamp() + "," +
                    acumTxs + "," +
                    acumTxs_no_remasc;

            System.out.println(line);
            acumTxs =0;
            acumTxs_no_remasc =0;
            try {
                fileWriter.write(line+"\n");
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
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
        int minBlock = 1;
        int maxBlock = maxBlockchainBlock; //3200_000;
        int step = 1; // must go trought all txs to count
        RskProvider provider = new RskProvider(args,minBlock,maxBlock,step);
        TransactionVolumeAnalyzer analyzer = new TransactionVolumeAnalyzer(provider);
        analyzer.loadTrieForEachBlock = false;
        provider.processBlockchain(analyzer);
    }
}
