export interface SocketEvent {
    op: 'insert' | 'delete' | 'replace' | 'updated' | 'invalidate';
    key: any;
    value: any;
}
export declare const Events: {
    join: string;
    leave: string;
    NEW_BLOCK: string;
    NEW_PENDING_TX: string;
    NEW_BLOCK_STAT: string;
    NEW_TX: string;
    NEW_UNCLE: string;
    pastTxsR: string;
    pastBlocksR: string;
    getAddressBalance: string;
    getAddressTokenBalance: string;
    getAddressTokenTransfers: string;
    getAddressTokenTransfersByHolder: string;
    getAddressAmountTokensOwned: string;
    getAddressMetadata: string;
    getAddressAllTokensOwned: string;
    getContract: string;
    getContractsCreatedBy: string;
    getBlock: string;
    getBlocks: string;
    getBlocksMined: string;
    getBlockByNumber: string;
    getTotalNumberOfBlocks: string;
    getTx: string;
    getTxs: string;
    getBlockTxs: string;
    getAddressTxs: string;
    getAddressTotalTxs: string;
    getPendingTxs: string;
    getPendingTxsOfAddress: string;
    getTotalNumberOfPendingTxs: string;
    getNumberOfPendingTxsOfAddress: string;
    getUncle: string;
    getUncles: string;
    getTotalNumberOfUncles: string;
    getExchangeRates: string;
    search: string;
    getAverageDifficultyStats: string;
    getAverageGasLimitStats: string;
    getAverageGasPriceStats: string;
    getAverageTxFeeStats: string;
    getSuccessfulTxStats: string;
    getFailedTxStats: string;
    getTxStats: string;
    getAverageBlockSizeStats: string;
    getAverageBlockTimeStats: string;
    getAverageNumberOfUnclesStats: string;
    getAverageHashRateStats: string;
    getAverageMinerRewardsStats: string;
};
export declare const SocketDefaultRooms: string[];