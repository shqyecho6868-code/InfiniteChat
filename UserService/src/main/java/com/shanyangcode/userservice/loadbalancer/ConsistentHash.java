package com.shanyangcode.userservice.loadbalancer;

import org.springframework.cloud.client.ServiceInstance;

import java.util.*;
@SuppressWarnings({"all"})
public class ConsistentHash {

    private TreeMap<Integer,String> Nodes = new TreeMap();

    private int VIRTUAL_NODES = 160;//虚拟节点个数，用户指定，默认160

    private List<ServiceInstance> instances = new ArrayList<>();//真实物理节点集合

    public HashMap<String,ServiceInstance> map = new HashMap<>();//将服务实例与url地址一一映射

    public ConsistentHash(List<ServiceInstance> instances){
        this.instances = instances;
        init();
    }

    public void init() {
        for (ServiceInstance instance : instances) {
            String url = instance.getUri().toString();
            Nodes.put(getHash(url), url);
            map.put(url ,instance);
            for(int i = 0; i < VIRTUAL_NODES; i++) {
                int hash = getHash(url + "#" + i );
                Nodes.put(hash, url);
            }
        }
    }

    //得到url地址
    public String getServer(String clientInfo) {
        if (Nodes.isEmpty()) {
            return null;
        }
        int hash = getHash(clientInfo);
        //得到大于该Hash值的子红黑树
        SortedMap<Integer,String> subMap = Nodes.tailMap(hash);
        //如果没有更大的节点，则回到哈希环的第一个节点
        Integer nodeIndex = subMap.isEmpty() ? Nodes.firstKey() : subMap.firstKey();
        return Nodes.get(nodeIndex);
    }
    //使用FNV1_32_HASH算法计算服务器的Hash值,这里不使用重写hashCode的方法，最终效果没区别
    private int getHash(String str) {
        final int p = 16777619;
        int hash = (int) 2166136261L;
        for (int i = 0; i < str.length(); i++) {
            hash = (hash^str.charAt(i))*p;
            hash +=hash <<13;
            hash ^=hash >>7;
            hash +=hash <<3;
            hash ^=hash >>17;
            hash +=hash <<5;
            //如果算出来的值为负数 取其绝对值
            if(hash < 0) {
                hash = Math.abs(hash);
            }
        }
        return hash;
    }


}
