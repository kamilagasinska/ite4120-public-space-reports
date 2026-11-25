package ee.taltech.ite4120.reports.addressregister;

import ee.taltech.ite4120.reports.addressregister.wire.AdsAadress;
import java.util.List;

/** The only place where the register's Estonian names become ours. */
public final class AddressMapper {

    private AddressMapper() {}

    public static AddressInfo toAddressInfo(List<AdsAadress> addresses) {
        if (addresses == null || addresses.size() != 1) {
            return null;
        }
        AdsAadress only = addresses.getFirst();
        /* Our column holds at most 30 characters. A longer code is not the contract we know. */
        if (only == null || only.ads_oid() == null || only.ads_oid().isBlank() || only.ads_oid().length() > 30) {
            return null;
        }
        return new AddressInfo(only.ads_oid(), only.taisaadress());
    }
}
