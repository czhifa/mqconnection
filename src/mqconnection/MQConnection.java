package mqconnection;

import xmlmessage.XMLMessage;
import com.ibm.mq.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MQConnection {
    private class MQStaticConnection {
        MQQueue putQueue = null;
        MQQueue getQueue = null;
        MQPutMessageOptions pmo = new MQPutMessageOptions();
        MQGetMessageOptions gmo = new MQGetMessageOptions();
        MQMessage requestMsg = new MQMessage();
        MQMessage responseMsg = new MQMessage();
        byte[] responseMsgData = null;
        String msg = null; 
        XMLMessage responseXmlMessage = null;
        
        public MQStaticConnection(String putQueueName, String getQueueName) {
            try {
                putQueue = queueMgr.accessQueue(putQueueName, MQC.MQOO_BIND_NOT_FIXED | MQC.MQOO_OUTPUT);
                getQueue = queueMgr.accessQueue(getQueueName, MQC.MQOO_INPUT_AS_Q_DEF | MQC.MQOO_OUTPUT);
                
                pmo.options = MQC.MQPMO_NEW_MSG_ID; 
                requestMsg.replyToQueueName = getQueueName;
                requestMsg.report=MQC.MQRO_PASS_MSG_ID; 
                requestMsg.format = MQC.MQFMT_STRING; 
                requestMsg.messageType=MQC.MQMT_REQUEST; 
                
                gmo.matchOptions=MQC.MQMO_MATCH_MSG_ID;
                gmo.options=MQC.MQGMO_WAIT;
                gmo.waitInterval=60000;
            } catch (MQException ex) {}
        }
        
        public void closeConnection() {
            try {
                putQueue.close();
                getQueue.close();
            }
            catch (Exception ex) {}
        }
    }
    
    String queueMgrName = null;
    String queueMgrHostname = null;
    int queueMgrPort = 0;
    String queueMgrChannel = null; 
    MQQueueManager queueMgr = null;
    MQStaticConnection sc = null;
    
    public MQConnection(String queueMgrName, String queueMgrHostname, int queueMgrPort, String queueMgrChannel) {
        this.queueMgrName = queueMgrName;
        this.queueMgrHostname = queueMgrHostname;
        this.queueMgrPort = queueMgrPort;
        this.queueMgrChannel = queueMgrChannel;
        
        MQEnvironment.hostname = this.queueMgrHostname;
        MQEnvironment.port = this.queueMgrPort;
        MQEnvironment.channel = this.queueMgrChannel;
        
        try {
            queueMgr = new MQQueueManager(queueMgrName);
        }
        catch (MQException ex) {
            System.out.println("Error connecting to queue manager. Reason: " + ex.reasonCode);
            System.out.println(ex.getMessage());
        }
    }
    
    public MQConnection() {
        System.out.println("MQConnection. Empty constructor.");
    }
    
    public boolean clearQueue(String putQueueName) {
        int depth = 0;
        
        try {
           int openOptions = MQC.MQOO_INQUIRE;  
           MQQueue queue = queueMgr.accessQueue(putQueueName, openOptions);  
           depth = queue.getCurrentDepth();  
           queue.close();
           
           openOptions = MQC.MQOO_INPUT_AS_Q_DEF;
           queue = queueMgr.accessQueue(putQueueName, openOptions);  
           MQMessage message = new MQMessage();
           
           for (int i = 0; i < depth; ++i) {
               queue.get(message);
               //message.clearMessage();
               message = null;
               message = new MQMessage();
           }
           queue.close();  
           System.out.println("Queue " + putQueueName + ": cleared");
           return true;
        } 
        catch (MQException ex) {  
            System.out.println("clearQueue(" + putQueueName + "): error");
            System.out.println(ex.toString());
            return false;
        }
    }
    
    public boolean sendMessage(String putQueueName, MQMessage message) {
        MQQueue putQueue = null;
        MQPutMessageOptions pmo = new MQPutMessageOptions();
        try {
            putQueue = queueMgr.accessQueue(putQueueName, MQC.MQOO_BIND_NOT_FIXED | MQC.MQOO_OUTPUT);
            pmo.options = MQC.MQPMO_NEW_MSG_ID; // The queue manager replaces the contents of the MsgId field in MQMD with a new message identifier.            
            putQueue.put(message, pmo);
            putQueue.close();
            System.out.println("sendMessageSingle: message sent to " + putQueueName);
            return true;
        } 
        // For JDK 1.7: catch(MQException | IOException ex) {
        
        // For JDK 1.5
        catch(Exception ex) {
            System.out.println("sendMessageSingle: error");
            System.out.println(ex.toString());
            return false;
        }
    }
    
    public XMLMessage messageToXML(MQMessage message) {
        try {
            byte[] data = new byte[message.getDataLength()];
            message.readFully(data, 0, message.getDataLength());
            return new XMLMessage(new String(data));
        } catch (Exception ex) {
            return null;
        }
    }
    
    public MQMessage browseMessage(String getQueueName) {
        MQQueue getQueue = null;
        MQGetMessageOptions gmo = new MQGetMessageOptions();
        MQMessage responseMsg = new MQMessage();
        byte[] responseMsgData = null;
        String msg = null;  
        
        try {
            getQueue = queueMgr.accessQueue(getQueueName, MQC.MQOO_BROWSE | MQC.MQOO_INPUT_SHARED);
            gmo.options = MQC.MQGMO_WAIT | MQC.MQGMO_BROWSE_NEXT ;
            getQueue.get(responseMsg, gmo);
            getQueue.close();

            System.out.println("browseMessage: message browsed from " + getQueueName);

            return responseMsg;
        } catch (Exception ex) {
            return null;
        }
    }
    
    public MQMessage getMessageSimple(String getQueueName) {
        MQQueue getQueue = null;
        MQGetMessageOptions gmo = new MQGetMessageOptions();
        MQMessage responseMsg = new MQMessage();
        byte[] responseMsgData = null;
        String msg = null;  
        
        try {
            getQueue = queueMgr.accessQueue(getQueueName, MQC.MQOO_INPUT_AS_Q_DEF | MQC.MQOO_OUTPUT);
            getQueue.get(responseMsg, gmo);
            getQueue.close();

            System.out.println("getMessageSimple: message recieved from " + getQueueName);

            return responseMsg;
        } catch (Exception ex) {
            return null;
        }
    }
    
    public XMLMessage getResponse(String putQueueName, String getQueueName, XMLMessage requestXmlMessage) {
        MQQueue putQueue = null;
        MQQueue getQueue = null;
        MQPutMessageOptions pmo = new MQPutMessageOptions();
        MQGetMessageOptions gmo = new MQGetMessageOptions();
        MQMessage requestMsg = new MQMessage();
        MQMessage responseMsg = new MQMessage();
        byte[] responseMsgData = null;
        String msg = null;        
        
        try {
            putQueue = queueMgr.accessQueue(putQueueName, MQC.MQOO_BIND_NOT_FIXED | MQC.MQOO_OUTPUT);
            pmo.options = MQC.MQPMO_NEW_MSG_ID; // The queue manager replaces the contents of the MsgId field in MQMD with a new message identifier.
            requestMsg.replyToQueueName = getQueueName; // the response should be put on this queue
            requestMsg.report=MQC.MQRO_PASS_MSG_ID; //If a report or reply is generated as a result of this message, the MsgId of this message is copied to the MsgId of the report or reply message.
            requestMsg.format = MQC.MQFMT_STRING; // Set message format. The application message data can be either an SBCS string (single-byte character set), or a DBCS string (double-byte character set). 
            requestMsg.messageType=MQC.MQMT_REQUEST; // The message is one that requires a reply.
            requestMsg.writeString(requestXmlMessage.toString()); // message payload
            putQueue.put(requestMsg, pmo);
            
            putQueue.close();
            
            System.out.println("newPutGetTransaction: request message sent to " + putQueueName);
        }
        catch (Exception ex) {
            System.out.println("newPutGetTransaction: error while sending request");
            System.out.println(ex.toString());
            return null;
        }
        
        try {    
            getQueue = queueMgr.accessQueue(getQueueName, MQC.MQOO_INPUT_AS_Q_DEF | MQC.MQOO_OUTPUT);
            responseMsg.messageId = requestMsg.messageId; // The Id to be matched against when getting a message from a queue
            //gmo.matchOptions=MQC.MQMO_MATCH_CORREL_ID; // The message to be retrieved must have a correlation identifier that matches the value of the CorrelId field in the MsgDesc parameter of the MQGET call.
            gmo.matchOptions=MQC.MQMO_MATCH_MSG_ID; // The message to be retrieved must have a correlation identifier that matches the value of the CorrelId field in the MsgDesc parameter of the MQGET call.
            gmo.options=MQC.MQGMO_WAIT; // The application waits until a suitable message arrives.
            gmo.waitInterval=60000; // timeout in ms
            getQueue.get(responseMsg, gmo);
            
            // Check the message content
            responseMsgData = responseMsg.readStringOfByteLength(responseMsg.getTotalMessageLength()).getBytes();
            
            getQueue.close();
            
            System.out.println("newPutGetTransaction: response message got from " + getQueueName);
            
            return new XMLMessage(new String(responseMsgData));
        } 
        // For JDK 1.7: catch(MQException | IOException ex) {
        
        // For JDK 1.5
        catch(Exception ex) {
            System.out.println("newPutGetTransaction: error while getting response");
            System.out.println(ex.toString());
            return null;
        }
    }
    
    public void newStaticConnection(String putQueueName, String getQueueName) {
        sc = new MQStaticConnection(putQueueName, getQueueName);
    }
    
    public void makeTransactionStaticConnection(XMLMessage requestXmlMessage) {
        // Request
        try {
            sc.requestMsg.writeString(requestXmlMessage.toString()); // message payload
            sc.putQueue.put(sc.requestMsg, sc.pmo);
            sc.requestMsg.clearMessage();
        
        } catch (Exception ex) {}
        // Response
        try {       
            sc.responseMsg.messageId = sc.requestMsg.messageId;
            sc.getQueue.get(sc.responseMsg, sc.gmo);
            sc.responseMsgData = sc.responseMsg.readStringOfByteLength(sc.responseMsg.getTotalMessageLength()).getBytes();
            sc.responseXmlMessage = new XMLMessage(new String(sc.responseMsgData));
            sc.responseMsg.clearMessage();
        } catch(Exception ex) {}
    }
    
    public void finalizeStaticConnection() {
        sc.closeConnection();
        sc = null;
    }
    
    public MQMessage newMessage(XMLMessage xmlMessage) {
        try {
            MQMessage message = new MQMessage();
            message.replyToQueueName = "SOMEQUEUE";
            message.report=MQC.MQRO_PASS_MSG_ID;
            message.format = MQC.MQFMT_STRING;
            message.messageType=MQC.MQMT_REQUEST;
            message.writeString(xmlMessage.toString());
            return message;
        } catch(Exception ex) {
            return null;
        }
    }
    
    public void closeConnection() {
        try {
            queueMgr.close();
        }
        catch (MQException ex) {
            System.out.println("Error while closing connection");
        }
        queueMgr = null;
    }
        
    @Override
    protected void finalize()
    { 
        try {
            queueMgr.close();
            
            queueMgrName = null;
            queueMgrHostname = null;
            queueMgrPort = 0;
            queueMgrChannel = null; 
            queueMgr = null;
            
            
        }
        catch (MQException ex) {
        }
        
        try {
            super.finalize();
        }
        catch (Throwable ex) {
            Logger.getLogger(MQConnection.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}