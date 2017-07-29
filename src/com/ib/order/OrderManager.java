/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.ib.order;

import com.ib.api.IBClient;
import org.apache.log4j.Logger;
import java.util.HashMap;
import com.ib.client.Order;
import com.ib.client.Types;
import java.util.Iterator;
import com.ib.config.*;
import com.ib.quote.QuoteManager;
import com.ib.position.*;

/**
 *
 * @author Siteng Jin
 */
public class OrderManager {
    private static final Logger LOG = Logger.getLogger(OrderManager.class);
    
    private IBClient m_client = null; 
    private double dynamicOffset = Double.MAX_VALUE;
    private double staticOffset = Double.MAX_VALUE;
    
    private HashMap<OrderidConidAction, Order> m_orderMap = null;
    
    public OrderManager( IBClient client){
        m_client = client;
        if(m_orderMap == null){
            m_orderMap = new HashMap<OrderidConidAction, Order>();
        }
    }
    
    public void startTrade(){
        
    }
    
    public synchronized void updateOrder(OrderidConidAction orderidConidAction, Order order){
        if(m_orderMap.containsKey(orderidConidAction)){
            m_orderMap.replace(orderidConidAction, order);
        } else {
            m_orderMap.put(orderidConidAction, order);
        }
    }
    
    public synchronized boolean verifyOrders(){
        LOG.info("Verifying orders...");
        boolean foundBuy = false;
        boolean foundSell = false;
        
        ConfigReader configReader = ConfigReader.getInstance();
        int tradeConid = Integer.parseInt(configReader.getConfig(Configs.TRADE_CONID));
        
        Iterator it = m_orderMap.entrySet().iterator();
        while(it.hasNext()){
            OrderidConidAction tmp = (OrderidConidAction) it.next();
            if(tmp.getConid() == tradeConid){
                if(tmp.getAction() == Types.Action.BUY && foundBuy){
                    LOG.info("Found more than two BUY orders. Stop program IMMEDIATELY and correct manually.");
                    return false;
                } else {
                    foundBuy = true;
                }
                
                if(tmp.getAction() == Types.Action.SELL && foundSell){
                    LOG.info("Found more than two SELL orders. Stop program IMMEDIATELY and correct manually.");
                    return false;
                } else {
                    foundSell = true;
                }
            }
        }
        return true;
    }
    
    private boolean calculateDynamicOffset(){
        ConfigReader configReader = ConfigReader.getInstance();
        int tradeConid = Integer.parseInt(configReader.getConfig(Configs.TRADE_CONID));
        double pos = m_client.getPositionManager().getPosition(tradeConid);
        double distortion_rate = Double.parseDouble(configReader.getConfig(Configs.DISTORTION_RATE));
        dynamicOffset = -1.0 * pos * distortion_rate;
        return true;
    }
    
    private boolean findStaticOffset(){
        ConfigReader configReader = ConfigReader.getInstance();
        staticOffset = Double.parseDouble(configReader.getConfig(Configs.STATIC_OFFSET));
        return true;
    }
}
