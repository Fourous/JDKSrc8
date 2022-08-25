package com.collection.arrayList;

import java.util.ArrayList;
import java.util.List;

public class ClassGetName {
    public static void main(String[] args) {
        Father[] father = new Son[]{};
        System.out.println(father.getClass());

        List<String> strList = new MyList();
        System.out.println(strList.toArray().getClass());
    }
    class Father {}
    class Son extends Father {}

    static class MyList extends ArrayList<String> {
        @Override
        public String[] toArray(){
            return new String[]{"a", "b", "c"};
        }
    }
}
