package ge.camora.erp.module.cashflow;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Default Georgian Agicap-style category tree seeded on first run when the
 * category store is empty. Categories are editable afterwards in the UI, so this
 * is only the starting point. Builtin id == code (stable across re-seeds).
 *
 * <p>The two UNCATEGORIZED sentinels are builtin and non-deletable: every
 * transaction the rule engine cannot place lands in the sentinel matching its
 * direction.
 */
public final class CashFlowCategoryDefaults {

    public static final String UNCATEGORIZED_INFLOW = "uncategorized_inflow";
    public static final String UNCATEGORIZED_OUTFLOW = "uncategorized_outflow";

    public static List<CashFlowCategory> list(LocalDateTime now) {
        List<CashFlowCategory> categories = new ArrayList<>();
        int order = 0;

        // — საოპერაციო საქმიანობა · შემოსავლები —
        categories.add(builtin("sales", CashFlowSection.OPERATING, CashFlowDirection.INFLOW, "რეალიზაცია / გაყიდვები", ++order, now));
        categories.add(builtin("other_income", CashFlowSection.OPERATING, CashFlowDirection.INFLOW, "სხვა შემოსავალი", ++order, now));

        // — საოპერაციო საქმიანობა · გასავლები —
        categories.add(builtin("inventory", CashFlowSection.OPERATING, CashFlowDirection.OUTFLOW, "მარაგები", ++order, now));
        categories.add(builtin("salaries", CashFlowSection.OPERATING, CashFlowDirection.OUTFLOW, "ხელფასები", ++order, now));
        categories.add(builtin("rent", CashFlowSection.OPERATING, CashFlowDirection.OUTFLOW, "ქირა", ++order, now));
        categories.add(builtin("utilities", CashFlowSection.OPERATING, CashFlowDirection.OUTFLOW, "კომუნალური", ++order, now));
        categories.add(builtin("taxes", CashFlowSection.OPERATING, CashFlowDirection.OUTFLOW, "გადასახადები", ++order, now));
        categories.add(builtin("other_expenses", CashFlowSection.OPERATING, CashFlowDirection.OUTFLOW, "სხვა ხარჯები", ++order, now));

        // — საინვესტიციო საქმიანობა —
        categories.add(builtin("asset_sale", CashFlowSection.INVESTING, CashFlowDirection.INFLOW, "აქტივების გაყიდვა", ++order, now));
        categories.add(builtin("capex", CashFlowSection.INVESTING, CashFlowDirection.OUTFLOW, "ძირითადი აქტივები", ++order, now));

        // — საფინანსო საქმიანობა —
        categories.add(builtin("loan_in", CashFlowSection.FINANCING, CashFlowDirection.INFLOW, "სესხის მიღება", ++order, now));
        categories.add(builtin("loan_out", CashFlowSection.FINANCING, CashFlowDirection.OUTFLOW, "სესხის დაფარვა", ++order, now));
        categories.add(builtin("dividends", CashFlowSection.FINANCING, CashFlowDirection.OUTFLOW, "დივიდენდები", ++order, now));

        // — Sentinels (non-deletable) —
        categories.add(builtin(UNCATEGORIZED_INFLOW, CashFlowSection.OPERATING, CashFlowDirection.INFLOW, "არაკლასიფიცირებული შემოსავალი", ++order, now));
        categories.add(builtin(UNCATEGORIZED_OUTFLOW, CashFlowSection.OPERATING, CashFlowDirection.OUTFLOW, "არაკლასიფიცირებული ხარჯი", ++order, now));

        return categories;
    }

    public static String uncategorizedId(CashFlowDirection direction) {
        return direction == CashFlowDirection.INFLOW ? UNCATEGORIZED_INFLOW : UNCATEGORIZED_OUTFLOW;
    }

    private static CashFlowCategory builtin(String code, CashFlowSection section, CashFlowDirection direction,
                                            String nameKa, int order, LocalDateTime now) {
        return new CashFlowCategory(code, code, section, direction, nameKa, order, true, now, now);
    }

    private CashFlowCategoryDefaults() {
    }
}
