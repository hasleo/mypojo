package erwins.util.lib;

import java.util.Random;

/**
 * 흐릿한 배경 색 템플릿을 제공한다. 
 */
public enum Colors {
    
    YELLOW("#FEFFE9"),
    SKY("#E5FDFF"),
    RED("#FFF5E9"),
    WHITE("#FFFFFF"),
    GREEN("#ECFFE5"),
    BLUE("#EDF0FF"),
    PURPLE("#FFEEFE");
    
    private final String color;
    
    private Colors(String color){
        this.color = color;
    }
    
    private static final int LENGTH = Colors.values().length;
    private static final Random RANDOM = new Random();
    
    /** 랜덤한 색상 하나를 가져온다. */
    public static String getRandomColor(){
        return Colors.values()[RANDOM.nextInt(LENGTH)].color;
    }
    
    
}
