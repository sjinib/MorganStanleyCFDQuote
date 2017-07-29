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
        orderManager.requestOpenOrder();
        if(!orderManager.verifyOrders()){
            return;
        }
    }
}
