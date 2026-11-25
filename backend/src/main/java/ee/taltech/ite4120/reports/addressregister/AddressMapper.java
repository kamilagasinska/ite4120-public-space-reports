package ee.taltech.ite4120.reports.addressregister;

import ee.taltech.ite4120.reports.addressregister.wire.AdsAadress;
import java.util.List;
import java.util.Objects;

/** The only place where the register's Estonian names become ours. */
public final class AddressMapper {

    private AddressMapper() {}

    public static AddressInfo toAddressInfo(List<AdsAadress> addresses) {
        List<AdsAadress> known = known(addresses);
        if (known.isEmpty()) {
            return null;
        }
        AdsAadress first = known.getFirst();
        /* Our column holds at most 30 characters. A longer code is not the contract we know. */
        boolean confirmed = known.size() == 1 && first.ads_oid() != null && !first.ads_oid().isBlank()
                && first.ads_oid().length() <= 30;
        boolean oneMunicipality = known.stream().map(AdsAadress::ehakov).distinct().count() == 1;
        return new AddressInfo(confirmed ? first.ads_oid() : null, confirmed ? first.taisaadress() : null,
                oneMunicipality ? blankToNull(first.ehakov()) : null,
                oneMunicipality ? blankToNull(first.omavalitsus()) : null);
    }

    /** At a point the register lists the nearest objects. The first one is the most exact. */
    public static AddressInfo toPlace(List<AdsAadress> addresses) {
        return known(addresses).stream().filter(a -> blankToNull(a.ehakov()) != null).findFirst()
                .map(a -> new AddressInfo(null, a.taisaadress(), a.ehakov(), blankToNull(a.omavalitsus())))
                .orElse(null);
    }

    private static List<AdsAadress> known(List<AdsAadress> addresses) {
        return addresses == null ? List.of() : addresses.stream().filter(Objects::nonNull).toList();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
