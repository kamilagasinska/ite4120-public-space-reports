package ee.taltech.ite4120.reports.addressregister;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.taltech.ite4120.reports.addressregister.http.HttpAddressRegisterAdapter;
import ee.taltech.ite4120.reports.addressregister.xroad.XRoadAddressRegisterAdapter;
import org.junit.jupiter.api.Test;

/** Both transports turn an unreachable register into the one exception the service fails open on. */
class AddressRegisterFailureTest {

    @Test
    void xRoadFailsClearlyWithoutASecurityServer() {
        var adapter = new XRoadAddressRegisterAdapter("http://127.0.0.1:9",
                "ee-test/EDU/taltech/ite4120", "ee-test/GOV/70003098/aks-ads/ADSobjotsingV8");
        assertThatThrownBy(() -> adapter.lookup("Raja 15")).isInstanceOf(AddressRegisterException.class)
                .hasMessageContaining("X-Road call failed");
    }

    @Test
    void httpFailsClearlyWhenNothingListens() {
        var adapter = new HttpAddressRegisterAdapter("http://127.0.0.1:9");
        assertThatThrownBy(() -> adapter.lookup("Raja 15")).isInstanceOf(AddressRegisterException.class)
                .hasMessageContaining("unreachable");
    }
}
