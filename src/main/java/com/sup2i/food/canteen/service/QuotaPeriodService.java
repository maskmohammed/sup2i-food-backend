package com.sup2i.food.canteen.service;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;

@Service
public class QuotaPeriodService {

    public QuotaPeriod resolve(
        String quotaPeriodType,
        LocalDate usageDate,
        LocalDate subscriptionStart,
        LocalDate subscriptionEnd,
        LocalDate entitlementStart,
        LocalDate entitlementEnd
    ) {

        requireDate(
            usageDate,
            "usageDate"
        );

        requireDate(
            subscriptionStart,
            "subscriptionStart"
        );

        requireDate(
            subscriptionEnd,
            "subscriptionEnd"
        );

        requireDate(
            entitlementStart,
            "entitlementStart"
        );

        requireDate(
            entitlementEnd,
            "entitlementEnd"
        );

        LocalDate naturalStart;
        LocalDate naturalEnd;

        switch (quotaPeriodType) {

            case "SUBSCRIPTION" -> {

                naturalStart =
                    subscriptionStart;

                naturalEnd =
                    subscriptionEnd;
            }

            case "WEEK" -> {

                int daysFromMonday =
                    usageDate
                        .getDayOfWeek()
                        .getValue()
                        - 1;

                naturalStart =
                    usageDate.minusDays(
                        daysFromMonday
                    );

                naturalEnd =
                    naturalStart.plusDays(6);
            }

            case "MONTH" -> {

                YearMonth month =
                    YearMonth.from(
                        usageDate
                    );

                naturalStart =
                    month.atDay(1);

                naturalEnd =
                    month.atEndOfMonth();
            }

            case "DAY" -> {

                naturalStart =
                    usageDate;

                naturalEnd =
                    usageDate;
            }

            default ->
                throw new IllegalStateException(
                    "Unsupported quota period type: "
                        + quotaPeriodType
                );
        }

        LocalDate start =
            max(
                naturalStart,
                subscriptionStart,
                entitlementStart
            );

        LocalDate end =
            min(
                naturalEnd,
                subscriptionEnd,
                entitlementEnd
            );

        boolean outside =
            usageDate.isBefore(start)
                || usageDate.isAfter(end);

        if (outside) {

            throw new IllegalStateException(
                "Usage date is outside the effective quota period."
            );
        }

        return new QuotaPeriod(
            start,
            end
        );
    }

    private LocalDate max(
        LocalDate first,
        LocalDate second,
        LocalDate third
    ) {

        LocalDate result =
            first;

        if (second.isAfter(result)) {
            result = second;
        }

        if (third.isAfter(result)) {
            result = third;
        }

        return result;
    }

    private LocalDate min(
        LocalDate first,
        LocalDate second,
        LocalDate third
    ) {

        LocalDate result =
            first;

        if (second.isBefore(result)) {
            result = second;
        }

        if (third.isBefore(result)) {
            result = third;
        }

        return result;
    }

    private void requireDate(
        LocalDate value,
        String label
    ) {

        if (value == null) {

            throw new IllegalArgumentException(
                label + " is required."
            );
        }
    }

    public record QuotaPeriod(
        LocalDate start,
        LocalDate end
    ) {
    }
}
