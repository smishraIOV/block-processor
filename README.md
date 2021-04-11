# block-processor

Tool that processes the RSK blockchain. To parse the RSK blockchain, this tool requires a running RSK node (or access to a RSK snapshot). The tool automatically reads RSK blockchain data from $HOME/.rsk.

# Examples

The following analyzers are given as examples:

## HashrateAnalyzer
This class dumps the RSK hashrate at several points in history.

## PowpegBalanceAnalyzer
This class dumps the balance of the Bridge contract at different points in time. This is the number  of bitcoins the bridge has detected has been pegged-in. Although this should match the amount of bitcoins transferred to the Powpeg in Bitcoin transactions, the two measures could diverge if there was a bug in RSK code or there are invalid peg-in transactions.

## StorageAnalyzer
This Analyzer scans the storage trie and counts accounts, contracts, and other metrics that allow to track the usage of RSK and also  help optimize the unitrie data structure.

## CallAnalyzer
This class counts the number of contract calls made by EOAs in some period.

## TransactionVolumeAnalyzer
This class analyzes the transaction volume per day at different points in RSK history. It dumps two values: transactions/day including REMASC transactions, and transactions/day excluding REMASC transactions.
