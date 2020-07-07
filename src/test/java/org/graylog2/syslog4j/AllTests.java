package org.graylog2.syslog4j;

import org.graylog2.syslog4j.impl.message.processor.AbstractSyslogMessageProcessorTest;
import org.graylog2.syslog4j.impl.unix.UnixSyslogTest;
import org.graylog2.syslog4j.server.impl.event.CiscoSyslogServerEventTest;
import org.graylog2.syslog4j.server.impl.event.FortiGateSyslogEventTest;
import org.graylog2.syslog4j.server.impl.event.SyslogServerEventTest;
import org.graylog2.syslog4j.server.impl.structured.TestMatchChar;
import org.graylog2.syslog4j.test.message.structured.StructuredSyslogMessageTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        StructuredSyslogMessageTest.class,
        TestMatchChar.class,
        SyslogServerEventTest.class,
        FortiGateSyslogEventTest.class,
        CiscoSyslogServerEventTest.class,
        UnixSyslogTest.class,
        AbstractSyslogMessageProcessorTest.class
})
public class AllTests {


}
