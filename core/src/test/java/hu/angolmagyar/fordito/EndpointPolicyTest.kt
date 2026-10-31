package hu.angolmagyar.fordito

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointPolicyTest {
    @Test fun allowsHttpsAndDefaultProviderEndpoint() {
        assertTrue(EndpointPolicy.isAllowed(""))
        assertTrue(EndpointPolicy.isAllowed("https://api.example.com/v1"))
    }

    @Test fun allowsOnlyLocalOrPrivateHttpEndpoints() {
        assertTrue(EndpointPolicy.isAllowed("http://localhost:11434/v1"))
        assertTrue(EndpointPolicy.isAllowed("http://127.0.0.1:11434/v1"))
        assertTrue(EndpointPolicy.isAllowed("http://10.0.0.4:11434/v1"))
        assertTrue(EndpointPolicy.isAllowed("http://172.20.0.4:11434/v1"))
        assertTrue(EndpointPolicy.isAllowed("http://192.168.1.10:11434/v1"))
    }

    @Test fun rejectsRemotePlainHttpAndMalformedEndpoints() {
        assertFalse(EndpointPolicy.isAllowed("http://api.example.com/v1"))
        assertFalse(EndpointPolicy.isAllowed("http://172.32.0.1/v1"))
        assertFalse(EndpointPolicy.isAllowed("http://192.168.1.999/v1"))
        assertFalse(EndpointPolicy.isAllowed("file:///tmp/endpoint"))
        assertFalse(EndpointPolicy.isAllowed("not a url"))
        assertFalse(EndpointPolicy.isAllowed("https://user:pass@example.com/v1"))
    }

    @Test fun enforcesRfc1918BoundariesAndRejectsLinkLocalOrCarrierGradeRanges() {
        assertTrue(EndpointPolicy.isAllowed("http://172.16.0.1:11434"))
        assertTrue(EndpointPolicy.isAllowed("http://172.31.255.254:11434"))
        assertFalse(EndpointPolicy.isAllowed("http://172.15.255.254:11434"))
        assertFalse(EndpointPolicy.isAllowed("http://172.32.0.1:11434"))
        assertFalse(EndpointPolicy.isAllowed("http://100.64.0.1:11434"))
        assertFalse(EndpointPolicy.isAllowed("http://169.254.1.1:11434"))
        assertFalse(EndpointPolicy.isAllowed("http://0.0.0.0:11434"))
    }

    @Test fun rejectsAmbiguousOrCredentialBearingEndpoints() {
        assertFalse(EndpointPolicy.isAllowed("http://127.0.0.1.evil.example"))
        assertFalse(EndpointPolicy.isAllowed("http://user@localhost:11434"))
        assertFalse(EndpointPolicy.isAllowed("http://localhost:11434/#fragment"))
        assertFalse(EndpointPolicy.isAllowed("ftp://192.168.1.2/file"))
        assertFalse(EndpointPolicy.isAllowed("https://"))
    }

}
