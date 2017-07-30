/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.ib.order;

import com.ib.api.IBClient;
import org.apache.log4j.Logger;
import com.ib.quote.QuoteManager;
import com.ib.position.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 * @author Siteng Jin
 */
public class Trader {
    private static final Logger LOG = Logger.getLogger(OrderManager.class);
    
    private IBClient m_client = null; 
    
    public Trader(IBClient client){
        m_client = client;
    }
    
    
    public void startTrade(){
        // 1. Checking open orders
        OrderManager orderManager = m_client.getOrderManager();        
        PositionManager positionManager = m_client.getPositionManager();
        QuoteManager quoteManager = m_client.getQuoteManager();
        
        positionManager.requestPosition();   
        if(!positionManager.confirmAllPositionReceived()){
            // TODO
        }
        
        quoteManager.requestSourceData();
        if(!quoteManager.confirmTickTypesReceived()){
            // TODO
        }
        
        orderManager.requestOpenOrder();
        if(!orderManager.verifyAndInitializeOrders()){
            LOG.debug("Abnormal order detected. Please correct order manually. Stoping program.");
            System.exit(0);
        }
    }

    /*
    public void startTrade(){
        while(true){
            try{
                LOG.debug("------------Not trading in order manager----------");
                Thread.sleep(5000);
            } catch (Exception e){
                
            }
        }
    }
*/
}
