package co.rsk.tools.processor.examples;

import co.rsk.tools.processor.RskBlockProcessor;
import co.rsk.tools.processor.RskProvider;
import org.ethereum.core.Transaction;

public class CallAnalyzer extends RskBlockProcessor {

    private int numCalls = 0;

    public int getNumCalls() {
        return numCalls;
    }

    @Override
    public void process() {
        for (Transaction transaction : this.currentBlock.getTransactionsList()) {
            byte[] code = trie.get(trieKeyMapper.getCodeKey(transaction.getReceiveAddress()));
            byte[] data = transaction.getData();

            if (code != null && data != null) {
                numCalls++;
            }
        }
    }

    public static void main (String args[]) {
        RskProvider provider = new RskProvider(args);
        CallAnalyzer analyzer = new CallAnalyzer();

        provider.processBlockchain(analyzer);

        System.out.println("There are " + analyzer.getNumCalls() + " calls.");
    }
}
