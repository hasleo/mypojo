package erwins.util.vender.hibernate;

import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import org.hibernate.Criteria;
import org.hibernate.LockMode;
import org.hibernate.Query;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.springframework.orm.hibernate3.support.HibernateDaoSupport;

import erwins.util.lib.Clazz;
import erwins.util.lib.Sets;
import erwins.util.root.EntityId;
import erwins.util.root.EntityInit;
import erwins.util.root.EntityOwnerValidator;
import erwins.util.tools.SearchMap;

/**
 * getOrder을 재정의 할것. <br> return 이 2개 이상일 경우 Object[]로 넘어온다. 주의!
 */
@SuppressWarnings("unchecked")
public abstract class GenericHibernateDao<Entity, ID extends Serializable> extends HibernateDaoSupport{

    private Class<Entity> persistentClass;
    
    public GenericHibernateDao() {
        this.persistentClass = (Class<Entity>)((ParameterizedType)getClass().getGenericSuperclass()).getActualTypeArguments()[0];
    }

    public Class<Entity> getPersistentClass() {
        return persistentClass;
    }
    
    // ===========================================================================================
    //                                    편의성 단축 메소드.
    // ===========================================================================================
    
    /**
     * 데이터 입력 후 목록을 iBatis등을 사용하여 다시 쿼리할때 세션이 유지된다면 늦은 insert가 실행된다.
     * 이러한 경우 flush를 이용하여 쿼리를 강제로 실행 시켜야 한다. 커밋되지는 않는다.
     **/
    public void flush(){
        getSession().flush();
    }    
    
    /** Flush 이후 메모리(1차캐시?)에서 삭제한다. */
    public void flushAndClear(){
        getSession().flush();
        getSession().clear();
    }
    
    protected Criteria getCriteria(){
        return getSession().createCriteria(getPersistentClass());
    }    
    
    // ===========================================================================================
    //                                    벨리데이션을 추가한 dao기능.
    // ===========================================================================================        

    /**
     * DefaultEntity의 하위노드라면 초기값을 세팅해 준다.
     * ID가 null이 아니면 update라고 판단하고 수정 가능 여부를 판별한다.
     * 업데이트 방식중 명시적으로 makePersistent를 호출해서 전부 교체하는 방식에만 사용된다.
     */
    public Entity makePersistent(Entity client) {
        if(client instanceof EntityInit){
            EntityInit defaultEntity = (EntityInit)client;
            defaultEntity.initValue();
        }
        if(client instanceof EntityOwnerValidator){ //성능 때문에 코드 증가.
            EntityOwnerValidator<ID> castedClient = (EntityOwnerValidator)client;
            if(castedClient.getId()!=null){
                EntityOwnerValidator server = (EntityOwnerValidator)getById(castedClient.getId());
                server.validateOwner();
                getSession().evict(server);  //중복객체 오류난다. 명시적으로 제거해주자.
            }
        }
        getSession().saveOrUpdate(client);
        return client;
    }
    
    /**
     *  소유주의 벨리데이션 체크를 검사한다.
     */
    private void validate(Entity entity) {
        if(entity instanceof EntityOwnerValidator){
            EntityOwnerValidator<ID> server = (EntityOwnerValidator)entity;
            server.validateOwner();
        }
    }

    public void makeTransient(Entity entity) {
        validate(entity);
        getSession().delete(entity);
    }

    /**
     * 를 거치는 삭제메소드. 
     */
    public void makeTransient(ID id) {
        Entity entity = getById(id,false);
        validate(entity);
        getSession().delete(entity);
    }
    
    // ===========================================================================================
    //                                      공용 카운트
    // ===========================================================================================

    /**
     * 페이징 등에서 사용될 전체 카운트 수
     * 복잡한 검색은 iBatis를 사용하고 아니라면 이것을 사용한다.
     **/
    protected int count(Criterion... criterion) {
        Criteria crit = getSession().createCriteria(getPersistentClass()).setProjection(
                Projections.projectionList().add(Projections.rowCount())
                );
        if(criterion != null) for (Criterion c : criterion) crit.add(c); //null이면 무시
        return Sets.getResultInt(crit.list());
    }
    
    /** 주어진 조건에 해당하는 자료가 1건 이상 있는지? */
    protected boolean isExist(Criterion... criterion) {
        int count = count(criterion);
        if(count > 0) return true;
        return false;
    }
    
    /** 주어진 조건에 해당하는 자료가 1건 이상 있는지? */
    public boolean isExist(ID id) {
    	return isExist(Restrictions.eq(EntityId.ID_NAME,id));
    }
    
    /** 주어진 조건에 해당하는 자료가 1건 이상 있는지? */
    protected boolean isExist(Collection<Criterion> col) {
        return isExist(col.toArray(new Criterion[col.size()]));
    }
    
    /** HQL버전이다. 반드시 1개의 숫자를 리턴해야 한다. */
    protected boolean isExist(String hql,Object ... parameters) {
        Query query = super.getSession().createQuery(hql);
        for(int i=0;i<parameters.length;i++){
            query.setParameter(i, parameters[i]);    
        }
        Integer count = Sets.getResultCount(query.list()).intValue();
        return count > 0 ? true : false; 
    }    
    
    // ===========================================================================================
    //                                      find
    // ===========================================================================================

    /**
     * 수정을 원할경우 lazy로 얻어오자. 일반 select일 경우 lock을 얻을 필요가 없다.
     * get()으로 참조시 id에 해당하는 값이 없으면 null을 리턴한다.
     */
    public Entity getById(ID id, boolean lock) {
        Entity entity;
        if (lock) entity = (Entity) getSession().load(getPersistentClass(), id, LockMode.UPGRADE);
        else entity = (Entity) getSession().get(getPersistentClass(), id);
        return entity;
    }
    
    /** id에 해당하는 객체가 없을경우 예외를 던진다. */
    public Entity getById(ID id) {
        Entity T =  getById(id,false);
        if(T==null) throw new RuntimeException(persistentClass.getSimpleName()+ " is not found (pk is "+id+"). you need debugging");
        return T;
    }
    
    /** 유일하지 않으면 예외를 던진다.*/
    protected Entity getUnique(Criterion... criterion) {
        return Sets.getResultUnique(findBy(criterion));
    }
    
    /** null이거나 1개만이 있을 수 있다. */
    protected Entity getUniqueNullable(Criterion... criterion) {
    	List<Entity> sets = findBy(criterion);
    	return Sets.getUniqNullable(sets);
    }
    
    /** null이거나 1개만이 있을 수 있다. */
    protected Entity getUniqueNullable(HqlBuilder hql) {
        Query query = hql.query(getSession());
        return Sets.getUniqNullable((List<Entity>)query.list());
    }
    
    /** null이거나 1개만이 있을 수 있다. 전체를 로딩하지 않고 인덱스만 읽어서 Proxy객체를 가져올때 사용된다. */
    protected Entity getUniqueForProxy(Criterion... criterion) {
    	Criteria crit = getSession().createCriteria(getPersistentClass());
        for (Criterion c : criterion)  crit.add(c);
        crit.setProjection(Projections.projectionList().add(Projections.property(EntityId.ID_NAME)));
        List<Object> list = crit.list();
        if(list.size()==0) return null;
    	else if(list.size()==1){
    		ID id = (ID)list.get(0);
    		Entity proxy = Clazz.instance(getPersistentClass());
    		Clazz.setObject(proxy, EntityId.ID_NAME, id);
    		return proxy;
    	}
    	else throw new IllegalStateException(list.size()+" collection nust be unique or zero size");
    }        

    // ===========================================================================================
    //                                    기타 공용
    // ===========================================================================================    
    
    public List<Entity> findAll(){
        return findBy();
    }
    public List<Entity> findAll(boolean cache){
    	return findBy(cache);
    }
    
    /** 이미 부모를 알고 있을때 자식 객체를 단독으로 가져오기 위해 사용한다. (이게 없으면 부모를 연관해 join해야 한다.) */
    protected List<Entity> filter(Collection<Entity> subEntitys,String etcSql,SearchMap map) {
    	Query q = getSession().createFilter(subEntitys,etcSql);
    	for(Entry entry : map.entrySet()) q.setParameter(entry.getKey().toString(), entry.getValue());
    	if(map.isPaging()){
            //map.setTotalCount(count(c.get())); //하지 않는다.
            q.setFirstResult(map.getSkipResults());
            q.setMaxResults(map.getPagingSize());            
        }
    	return q.list();
    }    
    
    // ===========================================================================================
    //                                      Criterion
    // ===========================================================================================    
    
    /** 간단한 검색용 . getOrder을 재정의 할것. 주로 테스트 용도로 사용한다.실전사용은 금지! */
    public List<Entity> findBy(Criterion... criterion) {
        return findBy(false,criterion);
    }
    
    /** 간단한 검색용 . getOrder을 재정의 할것. */
    protected List<Entity> findBy(boolean cache,Criterion... criterion) {
    	Criteria crit = getSession().createCriteria(getPersistentClass());
    	if(cache) crit.setCacheable(true);
        for (Criterion c : criterion)  crit.add(c);
        for (Order c : getDefaultOrder()) crit.addOrder(c);
        crit.setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);  //Set등의 배열이 있을 경우~
        return crit.list();
    }
    
    /** 조건이 있는 간단한 검색용 */
    protected List<Entity> findBy(Collection<Criterion> col) {
        return findBy(col.toArray(new Criterion[col.size()]));
    }
    /** 조건이 있는 간단한 검색용 */
    protected List<Entity> findBy(boolean cache,Collection<Criterion> col) {
        return findBy(cache,col.toArray(new Criterion[col.size()]));
    }
    
    /** 조인 같은거 안됨~ 간단한거만 사용. */
    protected void findBy(SearchMap map,CriteriaBuilder c) {
        Criteria criteria = getCriteria();
        for (Order each : getDefaultOrder()) criteria.addOrder(each);
        criteria.add(c.get());
        if(map.isPaging()){
            map.setTotalCount(count(c.get()));
            criteria.setFirstResult(map.getSkipResults());
            criteria.setMaxResults(map.getPagingSize());            
        }
        map.setResult(criteria.list());
    }       
    
    /** totalCount기능 없음. 단순 페이징 처리만 됨. */
    @Deprecated
    protected List<Entity> findBy(SearchMap map,Criterion ... criterion) {
        Criteria criteria = getCriteria();
        for (Order each : getDefaultOrder()) criteria.addOrder(each);
        for (Criterion c : criterion)  criteria.add(c);
        if(map.isPaging()){
            criteria.setFirstResult(map.getSkipResults());
            criteria.setMaxResults(map.getPagingSize());            
        }
        return criteria.list();
   }
    
    /** 프로시저 호출 */
    @Deprecated
    protected void callSP() {        
        //"{ call PG_DBR_CONNECT.SP_RECEIPT () }"
    }
    
    // ===========================================================================================
    //                                    HQL
    // ===========================================================================================
    
    /** 대량의 데이터를 배치처리 or 2차캐싱 할때 사용하자. id값만을 가져온다. */
    protected Iterator<Entity> iterator(HqlBuilder hql) {
        Query query = hql.query(getSession());
        query.iterate();
        return query.iterate();
    }
    
    /** HqlBuilder를 이용한 페이징 처리기. */
    protected void query(SearchMap map,HqlBuilder hql) {
        Query query = hql.query(getSession());
        if(map.isPaging()){
            Long count = (Long)hql.count(getSession()).uniqueResult();
            map.setTotalCount(count.intValue());
            query.setFirstResult(map.getSkipResults());
            query.setMaxResults(map.getPagingSize());            
        }
        map.setResult(query.list());
    }
    
    /** 페이징만 된다. 파라메터 매핑도 되지 않고 count역시 되지 않는다. */
    protected void query(SearchMap map,String hql) {
        Query query = super.getSession().createQuery(hql);
        if(map.isPaging()){
            query.setFirstResult(map.getSkipResults());
            query.setMaxResults(map.getPagingSize());            
        }
        map.setResult(query.list());
    }    
    
    /** map없이 사용할때. 단순 쿼리만 된다. */
    protected List<Entity> query(HqlBuilder hql) {
        Query query = hql.query(getSession());
        return query.list();
    }
    
    /** map없이 사용할때. */
    @Deprecated
    protected Entity queryUnique(HqlBuilder hql) {
        Query query = hql.query(getSession());
        return (Entity)query.uniqueResult();
    }
    
    /** 간단한 SQL 적용  */
    @Deprecated
    protected void querySql(SearchMap map,String sql,String count) {
        Query query =  getSession().createSQLQuery(sql);
        queryForSimplePaging(map,query,count);
    }
    
    /** HQL / Native SQL 구현시 페이징 적용  */
    @Deprecated
    protected void queryForSimplePaging(SearchMap map,Query query,String countSql){
        if(map.isPaging()){
            if(countSql!=null){
                Query c =  getSession().createSQLQuery(countSql);
                Long count = (Long)c.uniqueResult();
                map.setTotalCount(count.intValue());
            }
            query.setFirstResult(map.getSkipResults());
            query.setMaxResults(map.getPagingSize());            
        }
        map.setResult(query.list());
    }    
    
    // ===========================================================================================
    //                                        서브클래스에서 구현하시오.
    // ===========================================================================================

    /** 오버라이드 해서 사용.  안쓰는데도 있으니 abstract는 안달았음. */
    protected Order[] getDefaultOrder(){
        return new Order[0];
    }
    
    /*HQL 샘플
    --벌크 작업
    getSession().createSQLQuery("insert into t_code (id,name,sort,upper_id,is_use) values (999,'asd',1,'zzz',1)").executeUpdate();
    insert into DelinquentAccount (id, name) select c.id, c.name from Customer c where ...
    
    Query query = getSession().createQuery("update Custom a set a.companyId = :companyId where a.cuOwnerBid = :bid and a.companyId = :org ");
        .setLong("companyId", company.getCompanyId());
        .setLong("org", 0L);
        .setString("bid", company.getBid());
        return query.executeUpdate();
    
    
    --Map으로 매핑
    query.setResultTransformer(new AliasToEntityMapResultTransformer());
    
     == 크리테리아 ==
    -- 네이트브 SQL
    List cats = sess.createCriteria(Cat.class)
    .add( Restrictions.sqlRestriction("lower({alias}.name) like lower(?)", "Fritz%", Hibernate.STRING) )
    .list();
    
    --조인
    List cats = sess.createCriteria(Cat.class)
    .createAlias("kittens", "kt")
    .createAlias("mate", "mt")
    .add( Restrictions.eqProperty("kt.name", "mt.name") )
    .list();
    
    List cats = sess.createCriteria(Cat.class)
    .createCriteria("kittens", "kt")
        .add( Restrictions.eq("name", "F%") )
    .setResultTransformer(Criteria.ALIAS_TO_ENTITY_MAP)
    .list();
	Iterator iter = cats.iterator();
	while ( iter.hasNext() ) {
	    Map map = (Map) iter.next();
	    Cat cat = (Cat) map.get(Criteria.ROOT_ALIAS);
	    Cat kitten = (Cat) map.get("kt");
	}
	
	--페치
	.setFetchMode("kittens", FetchMode.EAGER)

	--그룹바이
	List results = session.createCriteria(Cat.class)
	    .setProjection( Projections.projectionList()
	        .add( Projections.rowCount() )
	        .add( Projections.avg("weight") )
	        .add( Projections.max("weight") )
	        .add( Projections.groupProperty("color") )
	    )
    .list();
    
    -- alias
    .as("colr")  or alias(..)
    Property.forName("weight").max().as("maxWeight")  ==  Projections.max("weight"), "maxWeight" 
    
    --서브쿼리
	DetachedCriteria avgWeightForSex = DetachedCriteria.forClass(Cat.class, "cat2")
	    .setProjection( Property.forName("weight").avg() )
	    .add( Property.forName("cat2.sex").eqProperty("cat.sex") );
	session.createCriteria(Cat.class, "cat")
	    .add( Property.forName("weight").gt(avgWeightForSex) )
	    .list();
    
    --캐시 최적화 by 자연키
	session.createCriteria(User.class)
    .add( Restrictions.naturalId()
        .set("name", "gavin")
        .set("org", "hb") 
    ).setCacheable(true)
    .uniqueResult();
    
    -- class or collection level
    @Filter(name="asd",condition=":myFilterParam = MY_FILTERED_COLUMN")
    session.enableFilter("myFilter").setParameter("myFilterParam", "some-value");
    
    */
}
