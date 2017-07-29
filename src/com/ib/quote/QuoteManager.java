/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.ib.quote;

import com.ib.api.IBClient;
import org.apache.log4j.Logger;
import com.ib.config.*;
import com.ib.client.Contract;
import java.text.DecimalFormat;

/**
 *
 * @author Siteng Jin
 */
public class QuoteManager {
    public static final int TICKERID = 1000;
    private static ConfigReader m_configReader = null;
    
    private static final Logger LOG = Logger.getLogger(QuoteManager.class);
    
    private IBClient m_client = null;
    
    private double bidPrice_n225m = -1;
    private double askPrice_n225m = -1;
    private double midPoint_n225m = -1;
    private double bidPrice_calculated = -1;
    private double askPrice_calculated = -1;
    
    private DecimalFormat df = new DecimalFormat("#.##");
    
    public QuoteManager(IBClient client){ 
        LOG.info("Initializing Quote Manager");
        m_client = client;
        if(m_configReader == null){
            m_configReader = ConfigReader.getInstance();
            m_configReader.readProperties();
        }
    }
    
    public void startRequestingSourceData(){
        Contract sourceContract = this.getSourceContract();
        m_client.getSocket().reqMktData(TICKERID, sourceContract, "", false, false, null);
        LOG.debug("Sent market data request for source contract. ConId = " + sourceContract.conid());
    }
    
    public synchronized void updateBidPrice(double price){
        bidPrice_n225m = price;
        if(askPrice_n225m != -1){
            midPoint_n225m = (bidPrice_n225m + askPrice_n225m)/2.0;
            midPoint_n225m = Double.valueOf(df.format(midPoint_n225m));
        }
        LOG.debug("Updated info: bidPrice_n225m = " + bidPrice_n225m + ", bidPrice_calculated = " + bidPrice_calculated + 
                ", midPoint_n225m = " + midPoint_n225m);
    }
    
    public synchronized void updateAskPrice(double price){
        askPrice_n225m = price;
        if(bidPrice_n225m != -1){
            midPoint_n225m = (bidPrice_n225m + askPrice_n225m)/2.0;
            midPoint_n225m = Double.valueOf(df.format(midPoint_n225m));
        }
        LOG.debug("Updated info: askPrice_n225m = " + askPrice_n225m + ", askPrice_calculated = " + askPrice_calculated + 
                ", midPoint_n225m = " + midPoint_n225m);
    }
    
    public synchronized double getCalculatedBidPrice(){
        LOG.debug("Returning calculated bid price = " + bidPrice_calculated);
        return this.bidPrice_calculated;
    }
    
    public synchronized double getCalculatedAskPrice(){
        LOG.debug("Returning calculated ask price = " + askPrice_calculated);
        return this.askPrice_calculated;
    }
    
    private Contract getSourceContract(){
        String sourceConid = m_configReader.getConfig(Configs.SOURCE_CONID);
        Contract sourceContract = new Contract();
        sourceContract.conid(Integer.parseInt(sourceConid));
        sourceContract.exchange("SMART");
        // PRODUCTION_CHANGE
        //sourceContract.exchange("OSE.JPN");
        return sourceContract;
    }
}
