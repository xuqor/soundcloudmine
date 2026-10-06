package xuqor.sound.client;
import org.junit.jupiter.api.Test;
import xuqor.sound.client.audio.QueueRules;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;
class QueueRulesTest {
    @Test void repeatOffStopsAtEnd(){assertEquals(-1,QueueRules.next(2,3,QueueRules.Repeat.OFF,false,true,new Random(1)));}
    @Test void repeatAllWraps(){assertEquals(0,QueueRules.next(2,3,QueueRules.Repeat.ALL,false,true,new Random(1)));}
    @Test void repeatOneReplaysOnEnd(){assertEquals(1,QueueRules.next(1,3,QueueRules.Repeat.ONE,false,true,new Random(1)));}
    @Test void manualNextDoesNotRepeatOne(){assertEquals(2,QueueRules.next(1,3,QueueRules.Repeat.ONE,false,false,new Random(1)));}
    @Test void emptyQueueStops(){assertEquals(-1,QueueRules.next(-1,0,QueueRules.Repeat.ALL,true,true,new Random(1)));}
    @Test void singletonShuffleStopsWithoutRepeat(){assertEquals(-1,QueueRules.next(0,1,QueueRules.Repeat.OFF,true,true,new Random(1)));}
    @Test void singletonRepeatAllLoops(){assertEquals(0,QueueRules.next(0,1,QueueRules.Repeat.ALL,true,true,new Random(1)));}
    @Test void repeatCycleHasThreeModes(){
        assertEquals(QueueRules.Repeat.ALL,QueueRules.Repeat.OFF.next());
        assertEquals(QueueRules.Repeat.ONE,QueueRules.Repeat.ALL.next());
        assertEquals(QueueRules.Repeat.OFF,QueueRules.Repeat.ONE.next());
    }
    @Test void shuffleNeverRepeatsCurrent(){
        for(int i=0;i<3;i++)for(int n=0;n<50;n++){
            int next=QueueRules.next(i,3,QueueRules.Repeat.OFF,true,true,new Random(n));
            assertNotEquals(i,next);assertTrue(next>=0&&next<3);
        }
    }
}
