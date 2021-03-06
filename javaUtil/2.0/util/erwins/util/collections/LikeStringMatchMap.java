package erwins.util.collections;

import java.util.List;
import java.util.Map;

import lombok.experimental.Delegate;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import erwins.util.root.NotThreadSafe;
import erwins.util.spring.SpringUtil;

/** 
 * 키워드들(짧은 단어)를 메모리에 미리 넣어놓고, 단어들(긴 단어)을 매칭할때 사용된다.
 *   */
@NotThreadSafe
public class LikeStringMatchMap<T> implements Map<String,T>{
	
	@Delegate
	private Map<String,T> map = Maps.newHashMap();
	private int minLength = 2;
	
	
	/** HashEntry의 key는 매핑된 텍스트.   */
	public List<HashEntry<T>> matchAny(String query){
		List<HashEntry<T>> result = Lists.newArrayList();
		for(String subText : SpringUtil.splitWord(query,minLength)){
			T value = map.get(subText);
			if(value==null) continue;
			result.add(new HashEntry<T>(subText,value));
		}
		return result;
	}
	
	/** matchAny와 동일하나 뒷 like로 매칭된다.  %문자  */
	public List<HashEntry<T>> matchAnySuffix(String query){
		List<HashEntry<T>> result = Lists.newArrayList();
		for(String subText : SpringUtil.splitWordSuffix(query,minLength)){
			T value = map.get(subText);
			if(value==null) continue;
			result.add(new HashEntry<T>(subText,value));
		}
		return result;
	}
	
	/** matchAny와 동일하나 앞 like로 매칭된다.  문자%  */
	public List<HashEntry<T>> matchAnyPrefix(String query){
		List<HashEntry<T>> result = Lists.newArrayList();
		for(String subText : SpringUtil.splitWordPrefix(query,minLength)){
			T value = map.get(subText);
			if(value==null) continue;
			result.add(new HashEntry<T>(subText,value));
		}
		return result;
	}
	
	

	public int getMinLength() {
		return minLength;
	}

	public LikeStringMatchMap<T> setMinLength(int minLength) {
		this.minLength = minLength;
		return this;
	}
	
	
	


}
