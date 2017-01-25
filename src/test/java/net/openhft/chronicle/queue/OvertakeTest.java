package net.openhft.chronicle.queue;

import net.openhft.chronicle.core.OS;
import net.openhft.chronicle.core.io.IOTools;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueue;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import net.openhft.chronicle.wire.DocumentContext;
import net.openhft.chronicle.wire.Wire;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Index runs away on double close - AM
 */
public class OvertakeTest {

    private String path;

    private long a_index;

    private int msgs = 500;
    @Before
    public void before() throws Exception {
        path = OS.TARGET + "/" + getClass().getSimpleName() + "-" + System.nanoTime();
        try (SingleChronicleQueue appender_queue = SingleChronicleQueueBuilder.binary(path)
              // .testBlockSize()
                   //     .rollCycle(TEST_DAILY)
                .buffered(false)
                        .build()) {
            ExcerptAppender appender = appender_queue.acquireAppender();
            for (int i = 0; i < msgs; i++) {
                final long l = i;
                appender.writeDocument(wireOut -> wireOut.write("log").marshallable(m -> {
                            m.write("msg").text("hello world ola multi-verse");
                            m.write("ts").int64(l);
                        }
                ));
            }
            a_index = appender.lastIndexAppended();
        }
    }

    @Test
    public void appendAndTail() throws Exception {
        SingleChronicleQueue tailer_queue = SingleChronicleQueueBuilder.binary(path)
               // .testBlockSize()
                //.rollCycle(TEST_DAILY)
                .buffered(false)
                .build();
        ExcerptTailer tailer = tailer_queue.createTailer();
        tailer = tailer.toStart();
        long t_index;
        t_index = doReadBad(tailer, msgs,false);
        assertEquals(a_index, t_index);
        tailer = tailer_queue.createTailer();
        tailer = tailer.toStart();
        t_index = doReadBad(tailer, msgs,true);
        assertEquals(a_index, t_index);

    }

    @After
    public void after() {
        try {
            IOTools.deleteDirWithFiles(path, 2);
        } catch (Exception ignored) {
        }
    }

    private static long doReadBad(ExcerptTailer tailer, int expected, boolean additionalClose) {
        int[] i = {0};
        long t_index = 0;
        while (true) {
            try (DocumentContext dc = tailer.readingDocument()) {

                Wire wire = dc.wire();
                if (wire==null)
                    break;
                t_index = tailer.index();

                dc.wire().read("log").marshallable(m -> {
                    String msg = m.read("msg").text();
                    assertNotNull(msg);
                    //System.out.println("msg:" + msg);
                    i[0]++;
                });
                if (additionalClose) {
                    dc.close();
                }
            }
        }
        assertEquals(expected, i[0]);
        return t_index;
    }

}
