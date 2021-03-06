
package erwins.util.xmpp;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.Roster;
import org.jivesoftware.smack.Roster.SubscriptionMode;
import org.jivesoftware.smack.RosterEntry;
import org.jivesoftware.smack.RosterGroup;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.RosterPacket;
import org.jivesoftware.smack.packet.XMPPError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import erwins.util.nio.ThreadUtil;
import erwins.util.root.exception.PropagatedRuntimeException;

/**
 * 경고 메세지 전송용으로 WAS에서 기동시, 쌍방향 친구추가를 해주자. 
 * 친추가 안되어있을경우 XMPPBotTokenClient$XMPPBotTokenMessageListener : error chat msg : null  로그를 보게될지도~
 * SMACK이라는 패키지가 필요하다.
 * 연결 시도하면  데몬 스래드들이 다수 기동된다.
 * Presence type :  available / unavailable
 *          status : 문자열 지정값
 *          
 *          XMPPException 래핑하던가 하자. 
 *  */
public class XMPPBotClient implements Iterable<RosterGroup>{
	
	private final ConnectionConfiguration connConfig;
    protected XMPPConnection connection ;
    private final String id;
    private final String pass;
    protected Logger log = LoggerFactory.getLogger(this.getClass());
    private ReadWriteLock  lock = new ReentrantReadWriteLock();
    private Lock readLock = lock.readLock();
    private Lock writeLock = lock.readLock();
	
    /** Google Talk으로 연결한다. */
    public XMPPBotClient(String id,String pass){
    	Roster.setDefaultSubscriptionMode(SubscriptionMode.accept_all);
		connConfig = new ConnectionConfiguration("talk.google.com", 5222,"gmail.com");
		connConfig.setReconnectionAllowed(true);
		this.id = id;
		this.pass = pass;
    }
    
	public XMPPBotClient(String host,int port,String serverName,String id,String pass){
		Roster.setDefaultSubscriptionMode(SubscriptionMode.accept_all);
		if(serverName!=null) connConfig = new ConnectionConfiguration(host, port,serverName);
		else connConfig = new ConnectionConfiguration(host, port);
		connConfig.setReconnectionAllowed(true);
		this.id = id;
		this.pass = pass;
	}
	
    public void sendPacket(Packet packet){
    	connection.sendPacket(packet);
    }
    
    private XMPPBotPacketListener botPacketListener;
    public void setBotPacketListener(XMPPBotPacketListener botPacketListener) {
		this.botPacketListener = botPacketListener;
	}
    private XMPPBotMessageListener botMessageListener;
	public void setBotMessageListener(XMPPBotMessageListener botMessageListener) {
		this.botMessageListener = botMessageListener;
	}

	public static interface XMPPBotPacketListener{
    	public void presence(Presence from);
    	public void unknown(IQ from);
    }
	public static interface XMPPBotMessageListener{
		public void message(Message from);
	}

    
    /** 채팅할게 아닌 특성상 RosterListener는 사용하지 않는다. 
     * 최초 접속시 RosterPacket이 조착해 초기정보를 세팅하는듯. */
    private final PacketListener packetListener = new PacketListener() {
    	
        public void processPacket(Packet p) {
            if (p instanceof Message) botMessageListener.message((Message) p);
            else if(p instanceof Presence) botPacketListener.presence((Presence) p);
            else if(p instanceof IQ){
            	if(p instanceof RosterPacket){
                    RosterPacket rp = (RosterPacket)p;
                    if(rp.getType() == IQ.Type.ERROR){
                        XMPPError e = rp.getError();
                        log.warn(e.getMessage());
                    }
            	}else{
            		botPacketListener.unknown((IQ)p); //추가.. 빈틈 없게
            	}
            }else botPacketListener.unknown((IQ)p);
        }
    };
    
    public boolean isConnected(){
    	if(connection==null) return false;
    	return connection.isConnected();
    }
    
    public void startup(){
        try {
        	connection = new XMPPConnection(connConfig);
            connection.connect();
            connection.login(id,pass);
            connection.addPacketListener(packetListener,null);
            connection.sendPacket(new Presence(Presence.Type.available));
        }
        catch (XMPPException e) {
        	throw new PropagatedRuntimeException(e);
        }
    }
    
    public void shutdown(){
    	connection.disconnect(new Presence(Presence.Type.unavailable));
    	connection.removePacketListener(packetListener);
    	connection = null;
    }
    
    public Collection<RosterEntry> getEntries() {
    	return connection.getRoster().getEntries();
    }
    public RosterEntry getEntrie(String id) {
    	return connection.getRoster().getEntry(id);
    }
    
	@Override
	public Iterator<RosterGroup> iterator() {
		return connection.getRoster().getGroups().iterator();
	}    
    
    /* ================================================================================== */
	/*                                   간편 API                                          */
	/* ================================================================================== */

	/** 그루핑은 XMPP서버와 XMPP클라이언트간에만 통신된다. 즉 타 클라이언트에게 영향을 미치치 않는다.
	 * 그룹과 엔트리는 N:1형식으로 매핑된다. 엔트리 하나에 여러 그룹이 올 수 있다. */
	public void addGroup(RosterEntry entry,String groupName){
		Roster r = connection.getRoster();
		RosterGroup group = r.getGroup(groupName);
		if(group==null) group = r.createGroup(groupName);
    	try {
    		if(!group.contains(entry)) group.addEntry(entry);
		} catch (XMPPException e) {
			throw new PropagatedRuntimeException(e);
		}
	}
	
	public void removeGroup(RosterEntry entry,String groupName){
		Roster r = connection.getRoster();
		RosterGroup group = r.getGroup(groupName);
		try {
			group.removeEntry(entry);
		} catch (XMPPException e) {
			throw new PropagatedRuntimeException(e);
		}
	}
	
	public void removeAllGroup(){
		Roster r = connection.getRoster();
		for(RosterGroup eachGroup : r.getGroups()){
			for(RosterEntry each : eachGroup.getEntries()){
				try {
					eachGroup.removeEntry(each);
				} catch (XMPPException e) {
					throw new PropagatedRuntimeException(e);
				}
			}
		}
	}
	
    /** bot이 해당 id의 Presence를 감지하도록 요청을 보낸다. */
    public void subscribe(String id){
    	Presence pp = new Presence(Presence.Type.subscribe);
    	pp.setTo(id);
    	connection.sendPacket(pp);
    }
    
    /** 간단 메시지를 전송한다. */
    public void  sendMessage(String mailAdress, String text){
    	readLock.lock();try{
    		Message response = new Message(mailAdress, Message.Type.chat);
            response.setBody(text);
            connection.sendPacket(response);	
    	}finally{
    		readLock.unlock();
    	}
    }
    
    /** 
     * 가끔 WAS와 구글서버간의 연결이 끊어질때가 있다. 그때 다시 연결해준다.  
     * 재연결 backoffSec는 2배씩 늘어난다. 최초 5초
     *  */
    public void  sendMessageOrRetry(String mailAdress, String text){
    	sendMessageOrRetry(mailAdress,text,5);
    }
    
    /** 
     * 예외를 좀더 자세하게 분석할 필요가 있다.
     * 일단 발견된건 아래 1개뿐
     * java.lang.IllegalStateException: Not connected to server.
     *  */
    private void sendMessageOrRetry(String mailAdress, String text,int backoffSec){
    	try {
			sendMessage(mailAdress,text);
		} catch (RuntimeException e) {
			log.error("전송중 예외가 발생했습니다. "+backoffSec+"초 후 재연결을 시도합니다. 이하는 예외로그",e);
			ThreadUtil.sleepIgnoreInterrupt(TimeUnit.SECONDS,backoffSec);
			
			//셧다운과 스타트업이 될때 다른 메세지가 호출되면 안된다. 이중 셧다운/스타트업이 될 수 있다.
			writeLock.lock();
			try{
	    		shutdown();
				startup();
				log.warn("재연결 성공");
	    	}finally{
	    		writeLock.unlock();
	    	}
			sendMessageOrRetry(mailAdress,text,backoffSec*2);			
		}
    }
    
     
    
    /** 여기서 ID는 도메인이 붙지 않는 순수 ID를 말한다. */
    public void createAccount(String id,String pass){
    	try {
			connection.getAccountManager().createAccount(id,pass);
		} catch (XMPPException e) {
			throw new PropagatedRuntimeException(e);
		}
    }
    /*
    *//** 그룹별 엔트리 정보를 리턴한다. *//*
    public JSONArray getEntryInfo(){
    	Roster roster = connection.getRoster();
		JSONArray root = new JSONArray();
		for(RosterGroup each :  roster.getGroups()){
			JSONObject obj = new JSONObject();
			obj.put("groupName", each.getName());
			JSONArray array = new JSONArray();
			for(RosterEntry entry : each.getEntries()){
				JSONObject eachJson = new JSONObject();
				Presence presence =  roster.getPresence(entry.getUser());
				eachJson.put("id", entry.getUser()); //JID
				eachJson.put("presenceType", presence.getType()); //able / unable
				eachJson.put("type", entry.getType());  //both / from
				eachJson.put("presenceMode", presence.getMode()); //chat 등  정의된 상태.
				eachJson.put("presenceStatus", presence.getStatus()); //텍스트 기반 클라이언트 지정 상태
				array.add(eachJson);
			}
			obj.put(Flex.CHILDREN, array);
			root.add(obj);
		}
		return root;
    }    */
    
    

}
