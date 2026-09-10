import { DateRangeValue, parseDateRangeInput } from '../../../main/ets/pages/components/DateRangeDialog';

/** Small pure checks shared by the ohosTest harness and native UI integration. */
export class DateRangeDialogChecks {
  static check(): void {
    const year: DateRangeValue | null = parseDateRangeInput('2026', '');
    if (year === null || year.start !== '2026-01-01' || year.end !== '2026-12-31') throw new Error('year range');
    const month: DateRangeValue | null = parseDateRangeInput('2026-02', '');
    if (month === null || month.start !== '2026-02-01' || month.end !== '2026-02-28') throw new Error('month range');
    const leapMonth: DateRangeValue | null = parseDateRangeInput('2024-02', '');
    if (leapMonth === null || leapMonth.end !== '2024-02-29') throw new Error('leap month');
    const day: DateRangeValue | null = parseDateRangeInput('2026-09-08', '');
    if (day === null || day.start !== day.end) throw new Error('day range');
    const range: DateRangeValue | null = parseDateRangeInput('2026-09', '2026-10');
    if (range === null || range.start !== '2026-09-01' || range.end !== '2026-10-31') throw new Error('explicit range');
    const openEnd: DateRangeValue | null = parseDateRangeInput('', '2026-10');
    if (openEnd === null || openEnd.start !== '' || openEnd.end !== '2026-10-31') throw new Error('open start');
    if (parseDateRangeInput('2026-02-30', '') !== null) throw new Error('invalid date accepted');
    if (parseDateRangeInput('2026-10', '2026-09') !== null) throw new Error('reversed range accepted');
    if (parseDateRangeInput('2026-9', '') !== null) throw new Error('non strict month accepted');
  }
}
