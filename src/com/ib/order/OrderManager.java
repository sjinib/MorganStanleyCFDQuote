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
import com.ib.client.Contract;
import com.ib.client.OrderType;

/**
 *
 * @author Siteng Jin
 */
public class OrderManager{
    private static final Logger LOG = Logger.getLogger(OrderManager.class);
    
    private static ConfigReader m_configReader = null;
    private static PositionManager m_positionManager = null;
    private static QuoteManager m_quoteManager = null;
    
    public static final Object OPENORDERLOCK = new Object();
    private static final Object ORDERACCESSLOCK = new Object();
    
    private IBClient m_client = null; 
    private int tradeConid = Integer.MAX_VALUE;
    private String tradeExchange = null;
    private int orderSizeDefault = Integer.MAX_VALUE; // Though order.totalquantity is double, still read as int to avoid confusion
    private String account = null;
    private int positionAdjustment = Integer.MAX_VALUE;
    
    private HashMap<Integer, OrderInfo> m_orderMap = null;
    private int buyOrderId = Integer.MAX_VALUE;
    private int sellOrderId = Integer.MAX_VALUE;
    
    public OrderManager( IBClient client){
        m_client = client;
        if(m_orderMap == null){
            m_orderMap = new HashMap<Integer, OrderInfo>();
        }
        if(m_configReader == null){
            m_configReader = ConfigReader.getInstance();
        }
        if(m_positionManager == null){
            m_positionManager = m_client.getPositionManager();
        }
        if(m_quoteManager == null){
            m_quoteManager = m_client.getQuoteManager();
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
        LOG.debug("Sent reqOpenOrders()");
    }
    
    public void updateOpenOrder(int orderId , Order order){
        synchronized(ORDERACCESSLOCK){
            if(m_orderMap.containsKey(orderId)){
                OrderInfo o = m_orderMap.get(orderId);
                o.setOrder(order);
                m_orderMap.replace(orderId, o);
                LOG.debug("Updated open order. Order: " + m_orderMap.get(orderId).getOrder().action() + " " +
                        m_orderMap.get(orderId).getOrder().totalQuantity() + ", Filled: " + m_orderMap.get(orderId).getFilled());
            } else {
                m_orderMap.put(orderId, new OrderInfo(order, Double.MAX_VALUE, Double.MAX_VALUE));
                LOG.debug("Added open order. Order: " + m_orderMap.get(orderId).getOrder().action() + " " +
                        m_orderMap.get(orderId).getOrder().totalQuantity());
            }
        }
    }
    
    public void updateOrderStatus(int orderId, double filled, double remaining){
        synchronized(ORDERACCESSLOCK){
            if(m_orderMap.containsKey(orderId)){
                OrderInfo o = m_orderMap.get(orderId);
                o.setFilled(filled);
                o.setRemaining(remaining);
                m_orderMap.replace(orderId, o);
                LOG.debug("Updated order status. Order: " + m_orderMap.get(orderId).getOrder().action() + " " +
                    m_orderMap.get(orderId).getOrder().totalQuantity() + ", Filled: " + m_orderMap.get(orderId).getFilled());
            }
            // Do not update status if no orderid is found
        }
    }
    
    public boolean verifyAndInitializeOrders(){
        // TODO: if need to synchonize for order verification
        LOG.debug("Verifying orders...");
        
        synchronized(OPENORDERLOCK){
            try {
                LOG.debug("Waiting for open order end to be received...");
                OPENORDERLOCK.wait();
            } catch (Exception e){
                LOG.error(e.getMessage(), e);
            }
        }
        
        LOG.debug("All orders are received, continue to verify order.");
        
        if(tradeConid == Integer.MAX_VALUE){
            findTradeConid();
        }
        
        if(m_orderMap.size() > 2){
            LOG.error("More than two orders are detected for source conid " + tradeConid + ", stopping program...");
            System.exit(0);
        }
        
        if(m_orderMap.isEmpty()){
            // No order found for TRADE_CONID, place new orders and return true
            LOG.info("No order is found for source conid, placing new BUY and SELL orders");
            placeNewBuyOrder();
            placeNewSellOrder();
            return true;
        } else {
            boolean foundBuy = false;
            boolean foundSell = false;
            
            Iterator it = m_orderMap.keySet().iterator();
            while(it.hasNext()){
                int orderId = (int) it.next();
                OrderInfo tmp = m_orderMap.get(orderId);
                if(tmp.getOrder().action() == Types.Action.BUY){
                    if(foundBuy){
                        LOG.error("Found more than two BUY orders for conid = " + tradeConid + ", stopping program...");
                        System.exit(0);
                    } else {
                        buyOrderId = orderId;
                        foundBuy = true;
                    }
                } else if(tmp.getOrder().action() == Types.Action.SELL){
                    if(foundSell){
                        LOG.error("Found more than two SELL orders for conid = " + tradeConid + ", stopping program...");
                        System.exit(0);
                    } else {
                        sellOrderId = orderId;
                        foundSell = true;
                    }
                }
            }
            
            // handle buy side order
            if(foundBuy){
                LOG.info("One BUY order is found. Updating order attributes");
                updateCurrentBuyOrder();
            } else {
                LOG.info("No BUY order is found. Placing new BUY order");
                placeNewBuyOrder();
            }
            
            if(foundSell){
                LOG.info("One SELL order is found. Updating order attributes");
                updateCurrentSellOrder();
            } else {
                LOG.info("No SELL order is found. Placing new BUY order");
                placeNewSellOrder();
            }
            
            return true;
        }        
    }
    
    private void findTradeConid(){
        tradeConid = Integer.parseInt(m_configReader.getConfig(Configs.TRADE_CONID));
    }
    
    private void findTradeExchange(){
        tradeExchange = m_configReader.getConfig(Configs.TRADE_EXCHANGE);
    }
    
    private void findOrderSizeDefault(){
        orderSizeDefault = Integer.parseInt(m_configReader.getConfig(Configs.ORDER_SIZE_DEFAULT));
    }
    
    private void findAccount(){
        account = m_configReader.getConfig(Configs.ACCOUNT);
    }
    
    private void findPositionAdjustment(){
        positionAdjustment = Integer.parseInt(m_configReader.getConfig(Configs.POSITION_ADJUSTMENT));
    }
    
    private void placeNewBuyOrder(){
        // Buy order should be placed on the trade bid price
        try{
            double tradeBidPrice = m_quoteManager.getTradeBidPrice();
            while(tradeBidPrice < 0){
                LOG.debug("Cannot get trade bid price. Try again in 200 ms...");
                Thread.sleep(200);
            }
            
            if(orderSizeDefault == Integer.MAX_VALUE){
                findOrderSizeDefault();
            }
            
            if(positionAdjustment == Integer.MAX_VALUE){
                findPositionAdjustment();
            }
            
            double pos = m_positionManager.getPosition();
            int totalQuantity = 0;
            if(pos >= 0.0){
                totalQuantity = orderSizeDefault - positionAdjustment;
            } else {
                totalQuantity = orderSizeDefault;
            }
            
            if(account == null){
                findAccount();
            }
            
            // Double check order size
            if(totalQuantity < Integer.MAX_VALUE){
                int orderId = m_client.getCurrentOrderIdAndIncrement();
                Contract tradeContract = getTradeContract();
                Order order = getLimitOrder(Types.Action.BUY, totalQuantity, tradeBidPrice, account);
                m_client.getSocket().placeOrder(orderId, tradeContract, order);
                LOG.info("Placed " + order.action() + " order (" + orderId + ") for " + tradeContract.conid() + "@" + tradeContract.exchange() + ": " + 
                        totalQuantity + "@" + tradeBidPrice);
            }
        } catch (Exception e){
            LOG.error(e.getMessage(), e);
        }
    }
    
    private void placeNewSellOrder(){
        // Sell order should be placed on the trade ask price
        try{
            double tradeAskPrice = m_quoteManager.getTradeAskPrice();
            while(tradeAskPrice < 0){
                LOG.debug("Cannot get trade ask price. Try again in 200 ms...");
                Thread.sleep(200);
            }
            
            if(orderSizeDefault == Integer.MAX_VALUE){
                findOrderSizeDefault();
            }
            
            if(positionAdjustment == Integer.MAX_VALUE){
                findPositionAdjustment();
            }
            
            double pos = m_positionManager.getPosition();
            int totalQuantity = 0;
            if(pos < 0.0){
                totalQuantity = orderSizeDefault - positionAdjustment;
            } else {
                totalQuantity = orderSizeDefault;
            }
            
            if(account == null){
                findAccount();
            }
            
            // Double check order size
            if(totalQuantity < Integer.MAX_VALUE){
                int orderId = m_client.getCurrentOrderIdAndIncrement();
                Contract tradeContract = getTradeContract();
                Order order = getLimitOrder(Types.Action.SELL, totalQuantity, tradeAskPrice, account);
                m_client.getSocket().placeOrder(orderId, tradeContract, order);
                LOG.info("Placed " + order.action() + " order (" + orderId + ") for " + tradeContract.conid() + "@" + tradeContract.exchange() + ": " + 
                        totalQuantity + "@" + tradeAskPrice);
            }
        } catch (Exception e){
            LOG.error(e.getMessage(), e);
        }
    }
    
    private void updateCurrentBuyOrder(){
        
    }
    
    private void updateCurrentSellOrder(){
        
    }
    
    private void cancelCurrentBuyOrder(){
        
    }
    
    private void cancelCurrentSellOrder(){
        
    }
    
    private Contract getTradeContract(){
        if(tradeConid == Integer.MAX_VALUE){
            findTradeConid();
        }
        if(tradeExchange == null){
            findTradeExchange();
        }
        Contract tradeContract = new Contract();
        tradeContract.conid(tradeConid);
        tradeContract.exchange(tradeExchange);
        return tradeContract;
    }
    
    private Order getLimitOrder(Types.Action action, double totalQuantity, double lmtPrice, String account){
        Order order = new Order();
        order.action(action);
        order.orderType(OrderType.LMT);
        order.totalQuantity(totalQuantity);
        order.lmtPrice(lmtPrice);
        order.account(account);
        return order;
    }
}
