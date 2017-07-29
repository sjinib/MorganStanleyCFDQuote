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
public class OrderManager{
    private static final Logger LOG = Logger.getLogger(OrderManager.class);
    
    private IBClient m_client = null; 
    private double dynamicOffset = Double.MAX_VALUE;
    private double staticOffset = Double.MAX_VALUE;
    
    
    private HashMap<Integer, OrderInfo> m_orderMap = null;
    
    public OrderManager( IBClient client){
        m_client = client;
        if(m_orderMap == null){
            m_orderMap = new HashMap<Integer, OrderInfo>();
        }
    }
    
    public void startTrade(){
        while(true){
            try{
                LOG.debug("------------Not trading in order manager----------");
                Thread.sleep(5000);
            } catch (Exception e){
                
            }
        }
    }
    
    public void requestOpenOrder(){
        m_client.getSocket().reqOpenOrders();
        LOG.debug("Send reqOpenOrders()");
    }
    
    public synchronized void updateOpenOrder(int orderId , Order order){
        if(m_orderMap.containsKey(orderId)){
            OrderInfo o = m_orderMap.get(orderId);
            o.setOrder(order);
            m_orderMap.replace(orderId, o);
        } else {
            m_orderMap.put(orderId, new OrderInfo(order, Double.MAX_VALUE, Double.MAX_VALUE));
        }
        LOG.debug("Updated open order. Order map: " + m_orderMap.get(orderId).getOrder().action() + " " + 
                m_orderMap.get(orderId).getOrder().totalQuantity() + ", Filled: " + m_orderMap.get(orderId).getFilled());
    }
    
    public synchronized void updateOrderStatus(int orderId, double filled, double remaining){
        if(m_orderMap.containsKey(orderId)){
            OrderInfo o = m_orderMap.get(orderId);
            o.setFilled(filled);
            o.setRemaining(remaining);
            m_orderMap.replace(orderId, o);
        }
        // Do not update status if no orderid is found
        LOG.debug("Updated open order. Order map: " + m_orderMap.get(orderId).getOrder().action() + " " + 
                m_orderMap.get(orderId).getOrder().totalQuantity() + ", Filled: " + m_orderMap.get(orderId).getFilled());
    }
    
    public synchronized boolean verifyOrders(){
        LOG.info("Verifying orders...");
        
        if(m_orderMap.isEmpty()){
            return true;
        }
        
        boolean foundBuy = false;
        boolean foundSell = false;
        
        ConfigReader configReader = ConfigReader.getInstance();
        int tradeConid = Integer.parseInt(configReader.getConfig(Configs.TRADE_CONID));
        
        Iterator it = m_orderMap.keySet().iterator();
        while(it.hasNext()){
            int orderId = (int) it.next();
            OrderInfo tmp = m_orderMap.get(orderId);
                if(tmp.getOrder().action() == Types.Action.BUY && foundBuy){
                    LOG.info("Found more than two BUY orders. Stop program IMMEDIATELY and correct manually.");
                    return false;
                } else {
                    foundBuy = true;
                }
                
                if(tmp.getOrder().action() == Types.Action.SELL && foundSell){
                    LOG.info("Found more than two SELL orders. Stop program IMMEDIATELY and correct manually.");
                    return false;
                } else {
                    foundSell = true;
                }
        }
        LOG.info("Verified orders.");
        return true;
    }
    
    private synchronized boolean calculateDynamicOffset(){
        ConfigReader configReader = ConfigReader.getInstance();
        int tradeConid = Integer.parseInt(configReader.getConfig(Configs.TRADE_CONID));
        double pos = m_client.getPositionManager().getPosition(tradeConid);
        double distortion_rate = Double.parseDouble(configReader.getConfig(Configs.DISTORTION_RATE));
        dynamicOffset = -1.0 * pos * distortion_rate;
        return true;
    }
    
    private synchronized boolean findStaticOffset(){
        ConfigReader configReader = ConfigReader.getInstance();
        staticOffset = Double.parseDouble(configReader.getConfig(Configs.STATIC_OFFSET));
        return true;
    }
}
