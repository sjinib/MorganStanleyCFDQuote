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
import com.ib.position.*;

/**
 *
 * @author Siteng Jin
 */
public class QuoteManager {
    public static final int TICKERID = 1000;
    private static ConfigReader m_configReader = null;
    
    private static final Logger LOG = Logger.getLogger(QuoteManager.class);
    
    public static final Object QUOTELOCK = new Object();
    private static final Object QUOTEACCESSLOCK = new Object();
    
    private IBClient m_client = null;
    
    private double sourceBidPrice = -1.0;
    private double sourceAskPrice = -1.0;
    private double sourceMidpoint = -1.0;
    private double tradeBidPrice = -1.0;
    private double tradeAskPrice = -1.0;
    
    private int sourceConid = Integer.MAX_VALUE;
    private String sourceExchange = null;
    private double staticOffset = Double.MAX_VALUE;
    
    private DecimalFormat df = new DecimalFormat("#.##");
    
    public QuoteManager(IBClient client){ 
        LOG.info("Initializing Quote Manager");
        m_client = client;
        if(m_configReader == null){
            m_configReader = ConfigReader.getInstance();
        }
    }
    
    public void requestSourceData(){
        Contract sourceContract = this.getSourceContract();
        m_client.getSocket().reqMktData(TICKERID, sourceContract, "", false, false, null);
        LOG.debug("Sent market data request for source contract. ConId = " + sourceContract.conid());
    }
    
    public void updateBidPrice(double price){
        synchronized(QUOTEACCESSLOCK){
            sourceBidPrice = price;
            if(sourceAskPrice != -1.0){
                sourceMidpoint = (sourceBidPrice + sourceAskPrice)/2.0;
                sourceMidpoint = Double.valueOf(df.format(sourceMidpoint));
            }
            LOG.debug("Updated info: sourceBidPrice = " + sourceBidPrice + ", tradeBidPrice = " + tradeBidPrice +
                    ", sourceMidpoint = " + sourceMidpoint);
            calculateTradeBidPrice();
        }
    }
    
    public void updateAskPrice(double price){
        synchronized(QUOTEACCESSLOCK){
            sourceAskPrice = price;
            if(sourceBidPrice != -1.0){
                sourceMidpoint = (sourceBidPrice + sourceAskPrice)/2.0;
                sourceMidpoint = Double.valueOf(df.format(sourceMidpoint));
            }
            LOG.debug("Updated info: sourceAskPrice = " + sourceAskPrice + ", tradeAskPrice = " + tradeAskPrice +
                    ", sourceMidpoint = " + sourceMidpoint);
            calculateTradeAskPrice();
        }
    }
    
    public boolean calculateTradeBidPrice(){
        if(staticOffset == Double.MAX_VALUE){
            staticOffset = Double.parseDouble(m_configReader.getConfig(Configs.STATIC_OFFSET));
        }
        
        double dynamicOffset = m_client.getPositionManager().getDynamicOffset();
        if(sourceBidPrice != -1 && dynamicOffset != Double.MAX_VALUE){
            tradeBidPrice = Double.valueOf(df.format(sourceBidPrice + staticOffset + dynamicOffset)); 
            LOG.debug("Calculated trade bid price = " + sourceBidPrice + "(sourceBidPrice) + " + 
                    staticOffset + "(staticOffset) + " + dynamicOffset + "(dynamicOffset) = " + tradeBidPrice);
            return true;
        } else {
            LOG.debug("Failed to calculate trade bid price because either source bid price or dynamic offset is missing");
            return false;
        }
    }
    
    public boolean calculateTradeAskPrice(){
        if(staticOffset == Double.MAX_VALUE){
            staticOffset = Double.parseDouble(m_configReader.getConfig(Configs.STATIC_OFFSET));
        }
        
        double dynamicOffset = m_client.getPositionManager().getDynamicOffset();
        if(sourceAskPrice != -1 && dynamicOffset != Double.MAX_VALUE){
            tradeAskPrice = Double.valueOf(df.format(sourceAskPrice + staticOffset + dynamicOffset)); 
            LOG.debug("Calculated trade ask price = " + sourceAskPrice + "(sourceAskPrice) + " + 
                    staticOffset + "(staticOffset) + " + dynamicOffset + "(dynamicOffset) = " + tradeAskPrice);
            return true;
        } else {
            LOG.debug("Failed to calculate trade ask price because either source ask price or dynamic offset is missing");
            return false;
        }
    }
    
    public double getTradeBidPrice(){
        synchronized(QUOTEACCESSLOCK){
            return this.tradeBidPrice;
        }
    }
    
    public double getTradeAskPrice(){
        synchronized(QUOTEACCESSLOCK){
            return this.tradeAskPrice;
        }
    }
    
    private Contract getSourceContract(){
        if(sourceConid == Integer.MAX_VALUE){
            sourceConid = Integer.parseInt(m_configReader.getConfig(Configs.SOURCE_CONID));
        }
        if(sourceExchange == null){
            sourceExchange = m_configReader.getConfig(Configs.SOURCE_EXCHANGE);
        }
        Contract sourceContract = new Contract();
        sourceContract.conid(sourceConid);
        sourceContract.exchange(sourceExchange);
        return sourceContract;
    }
    
    public boolean confirmTickTypesReceived(){
        LOG.debug("Verifying if all tick types are received for order placement...");
        
        synchronized(QUOTELOCK){
            while(sourceBidPrice == -1.0 || sourceAskPrice == -1.0 || sourceMidpoint == -1.0 ||
                    tradeBidPrice == -1.0 || tradeAskPrice == -1.0){
                try {
                    LOG.debug("Waiting for more quotes to be received...");
                    QUOTELOCK.wait();
                } catch (Exception e){
                    LOG.error(e.getMessage(), e);
                    return false;
                }
            }
        }
        
        LOG.debug("Confirm all tick types are received.");
        return true;
    }
}
