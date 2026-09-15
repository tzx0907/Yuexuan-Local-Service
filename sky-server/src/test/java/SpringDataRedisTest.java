import com.sky.SkyApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.core.*;

import java.util.Set;

@SpringBootTest(classes = SkyApplication.class, properties = "sky.websocket.enabled=false")
@Disabled("Manual Redis data-structure exercise; mutates the configured Redis instance")
public class SpringDataRedisTest {
        @Autowired
        private RedisTemplate redisTemplate;
        
        @Test
        public void testRedis() {
            System.out.println(redisTemplate);
            ValueOperations valueOperations = redisTemplate.opsForValue();
            HashOperations hashOperations = redisTemplate.opsForHash();
            ListOperations listOperations = redisTemplate.opsForList();
            SetOperations setOperations = redisTemplate.opsForSet();
            ZSetOperations zSetOperations = redisTemplate.opsForZSet();
        }
        
        @Test
        public void testString(){
            redisTemplate.delete("name");
            redisTemplate.delete("age");
            redisTemplate.delete("lock");
            
            redisTemplate.opsForValue().set("name","张三");
            redisTemplate.opsForValue().set("age",18);
            System.out.println(redisTemplate.opsForValue().get("name"));
            redisTemplate.opsForValue().setIfAbsent("lock","1");
            redisTemplate.opsForValue().setIfAbsent("lock","2");
        }
        
        @Test
        public void testHash(){
            redisTemplate.delete("user");
            
            redisTemplate.opsForHash().put("user","name","张三");
            redisTemplate.opsForHash().put("user","age",18);
            System.out.println(redisTemplate.opsForHash().get("user","name"));
            System.out.println(redisTemplate.opsForHash().get("user","age"));
            redisTemplate.opsForHash().putIfAbsent("user","sex","男");
            redisTemplate.opsForHash().putIfAbsent("user","sex","女");
            System.out.println(redisTemplate.opsForHash().get("user","sex"));
            System.out.println(redisTemplate.opsForHash().keys("user"));
            System.out.println(redisTemplate.opsForHash().values("user"));
            redisTemplate.opsForHash().delete("user","name");
            System.out.println(redisTemplate.opsForHash().get("user","name"));
        }
        
        @Test
        public void testList(){
            redisTemplate.opsForList().leftPush("students","张三","赵六");
            redisTemplate.opsForList().leftPush("students","李四");
            redisTemplate.opsForList().leftPush("students","王五");
            
            System.out.println("列表长度: " + redisTemplate.opsForList().size("students"));
            System.out.println("获取所有元素: " + redisTemplate.opsForList().range("students", 0, -1));
            System.out.println("获取第一个元素: " + redisTemplate.opsForList().index("students", 0));
            
            System.out.println("弹出左侧元素: " + redisTemplate.opsForList().leftPop("students"));
            System.out.println("弹出右侧元素: " + redisTemplate.opsForList().rightPop("students"));
            System.out.println("剩余元素: " + redisTemplate.opsForList().range("students", 0, -1));
        }
        
        @Test
        public void testSet(){
            redisTemplate.opsForSet().add("fruits","苹果");
            redisTemplate.opsForSet().add("fruits","香蕉");
            redisTemplate.opsForSet().add("fruits","橙子");
            redisTemplate.opsForSet().add("fruits","苹果");
            
            System.out.println("集合成员: " + redisTemplate.opsForSet().members("fruits"));
            System.out.println("集合大小: " + redisTemplate.opsForSet().size("fruits"));
            System.out.println("是否包含香蕉: " + redisTemplate.opsForSet().isMember("fruits","香蕉"));
            
            redisTemplate.opsForSet().remove("fruits","苹果");
            System.out.println("删除后的成员: " + redisTemplate.opsForSet().members("fruits"));
        }
        
        @Test
        public void testSetUnion(){
            redisTemplate.delete("set1");
            redisTemplate.delete("set2");
            redisTemplate.delete("union_result");
            
            redisTemplate.opsForSet().add("set1","A","B","C","D");
            redisTemplate.opsForSet().add("set2","C","D","E","F");
            
            System.out.println("集合1: " + redisTemplate.opsForSet().members("set1"));
            System.out.println("集合2: " + redisTemplate.opsForSet().members("set2"));
            
            System.out.println("并集（不存储）: " + redisTemplate.opsForSet().union("set1","set2"));
            
            redisTemplate.opsForSet().unionAndStore("set1","set2","union_result");
            System.out.println("并集结果（已存储）: " + redisTemplate.opsForSet().members("union_result"));
        }
        
        @Test
        public void testSetIntersect(){
            redisTemplate.delete("set1");
            redisTemplate.delete("set2");
            redisTemplate.delete("intersect_result");
            
            redisTemplate.opsForSet().add("set1","A","B","C","D");
            redisTemplate.opsForSet().add("set2","C","D","E","F");
            
            System.out.println("集合1: " + redisTemplate.opsForSet().members("set1"));
            System.out.println("集合2: " + redisTemplate.opsForSet().members("set2"));
            
            System.out.println("交集（不存储）: " + redisTemplate.opsForSet().intersect("set1","set2"));
            
            redisTemplate.opsForSet().intersectAndStore("set1","set2","intersect_result");
            System.out.println("交集结果（已存储）: " + redisTemplate.opsForSet().members("intersect_result"));
        }
        
        @Test
        public void testSetDifference(){
            redisTemplate.delete("set1");
            redisTemplate.delete("set2");
            redisTemplate.delete("diff_result");
            
            redisTemplate.opsForSet().add("set1","A","B","C","D");
            redisTemplate.opsForSet().add("set2","C","D","E","F");
            
            System.out.println("集合1: " + redisTemplate.opsForSet().members("set1"));
            System.out.println("集合2: " + redisTemplate.opsForSet().members("set2"));
            
            System.out.println("差集（set1 - set2）: " + redisTemplate.opsForSet().difference("set1","set2"));
            System.out.println("差集（set2 - set1）: " + redisTemplate.opsForSet().difference("set2","set1"));
            
            redisTemplate.opsForSet().differenceAndStore("set1","set2","diff_result");
            System.out.println("差集结果（已存储）: " + redisTemplate.opsForSet().members("diff_result"));
        }
        
        @Test
        public void testZSet(){
            redisTemplate.opsForZSet().add("scores","张三",95.5);
            redisTemplate.opsForZSet().add("scores","李四",88.0);
            redisTemplate.opsForZSet().add("scores","王五",92.3);
            
            System.out.println("所有成员（按分数排序）: " + redisTemplate.opsForZSet().range("scores", 0, -1));
            System.out.println("张三的分数: " + redisTemplate.opsForZSet().score("scores","张三"));
            System.out.println("集合大小: " + redisTemplate.opsForZSet().size("scores"));
            
            System.out.println("分数在90-100之间的成员: " + redisTemplate.opsForZSet().rangeByScore("scores", 90, 100));
            System.out.println("按分数降序排列: " + redisTemplate.opsForZSet().reverseRange("scores", 0, -1));
            
            redisTemplate.opsForZSet().remove("scores","李四");
            System.out.println("删除后的成员: " + redisTemplate.opsForZSet().range("scores", 0, -1));
        }
        @Test
        public void common(){
            Set keys =redisTemplate.keys("*");
            System.out.println(keys);
            boolean name =redisTemplate.hasKey("name");
            System.out.println(name);
            for(Object key:keys){
                DataType type = redisTemplate.type(key);
                System.out.println(key+"---"+type);
            }
            redisTemplate.delete("name");
            boolean name1 =redisTemplate.hasKey("name");
            System.out.println(name1);
        }
}
