import { Link } from 'react-router-dom'
import { LegalPage, Placeholder, type LegalSection } from '../../components/legal/LegalPage'

/**
 * Every claim on this page is checked against
 * `auth/service/AccountDeletionService.java`. Where the platform does not yet
 * do something, this page says so plainly rather than describing the
 * intended behaviour — a deletion policy that overstates what is deleted is
 * the single most damaging thing a storage product can publish.
 */
const sections: LegalSection[] = [
  {
    id: 'overview',
    title: 'What this page covers',
    body: (
      <>
        <p>
          You can delete your AstraStore account at any time from{' '}
          <Link to="/dashboard/settings" className="text-accent-text underline underline-offset-2">
            Settings
          </Link>
          . This page describes exactly what happens when you do, what is removed
          immediately, and what is retained.
        </p>
        <p>
          Deleting your account is irreversible. There is no grace period and no
          recovery process: once the request completes, we cannot restore your
          account, your credentials or your access.
        </p>
      </>
    ),
  },
  {
    id: 'how',
    title: 'How to delete your account',
    body: (
      <>
        <ol className="ml-5 list-decimal space-y-1.5">
          <li>Sign in and open Settings.</li>
          <li>Open the “Delete account” section at the bottom of the page.</li>
          <li>Confirm with your current password.</li>
          <li>Confirm the typed acknowledgement.</li>
        </ol>
        <p>
          We ask for your password because deletion is destructive and must not be
          possible from a session someone else left open. API keys cannot perform
          this action — it requires an interactive sign-in.
        </p>
      </>
    ),
  },
  {
    id: 'removed',
    title: 'What is removed immediately',
    body: (
      <>
        <p>When your deletion request completes, the following are destroyed:</p>
        <ul className="ml-5 list-disc space-y-1.5">
          <li>Your user record, including your username, email address and password hash.</li>
          <li>Every API key you have issued. They stop authenticating at once.</li>
          <li>Every active session and refresh token, signing you out everywhere.</li>
          <li>Any outstanding password-reset tokens.</li>
        </ul>
      </>
    ),
  },
  {
    id: 'retained',
    title: 'What is retained, and why',
    body: (
      <>
        <p>
          <strong className="font-semibold text-ink">Security audit records are anonymised, not deleted.</strong>{' '}
          We keep a log of security-relevant events — sign-ins, failed sign-in
          attempts, key issuance, permission changes. On deletion, the entries
          associated with your account have their identifying details replaced with
          an anonymised marker that cannot be linked back to you.
        </p>
        <p>
          We retain the anonymised records because they are how we detect and
          investigate attacks against the platform as a whole. An attacker who could
          erase the record of their own activity by deleting the account they used
          would make that log worthless. Anonymised entries are removed on the normal
          audit retention schedule described in our{' '}
          <Link to="/privacy" className="text-accent-text underline underline-offset-2">
            Privacy Policy
          </Link>
          .
        </p>
        <p>
          We also retain an internal record that a deletion was requested, so the
          request can be verified as having been carried out.
        </p>
      </>
    ),
  },
  {
    id: 'objects',
    title: 'Your stored objects',
    body: (
      <>
        <div className="rounded-lg border border-warning-border bg-warning-subtle p-4">
          <p className="font-medium text-ink">
            Operator action required before publication
          </p>
          <p className="mt-1.5 text-[13.5px] leading-relaxed text-ink-2">
            In the current release, deleting your account removes your credentials and
            access immediately, but the objects and buckets you uploaded are not yet
            erased from storage automatically. The deletion request is recorded and the
            data is purged by <Placeholder>[DESCRIBE THE PROCESS AND TIMEFRAME]</Placeholder>.
          </p>
          <p className="mt-1.5 text-[13.5px] leading-relaxed text-ink-2">
            Do not publish this page until this section states the real behaviour and
            timeframe. If object purging is not yet automated, either automate it or
            describe the manual process honestly, including how a user can request and
            confirm it.
          </p>
        </div>
        <p>
          If you want your stored objects removed before deleting your account, delete
          them yourself first: move them to Trash, then empty the Trash. Emptying the
          Trash destroys the stored data and cannot be undone.
        </p>
      </>
    ),
  },
  {
    id: 'admin',
    title: 'Accounts deleted by an administrator',
    body: (
      <>
        <p>
          An administrator can delete an account from the user administration screen.
          The effect on your data is identical to deleting it yourself.
        </p>
        <p>
          The platform will not allow the last remaining administrator account to be
          deleted or demoted, because that would leave the deployment permanently
          unadministrable.
        </p>
      </>
    ),
  },
  {
    id: 'contact',
    title: 'Requesting deletion another way',
    body: (
      <>
        <p>
          If you cannot access your account, contact us at{' '}
          <Placeholder>[CONTACT EMAIL]</Placeholder> and we will verify your identity
          before acting. We will respond within{' '}
          <Placeholder>[RESPONSE WINDOW]</Placeholder>.
        </p>
        <p>
          We will never action a deletion request on the strength of an email address
          alone, because that would let anyone destroy someone else's data.
        </p>
      </>
    ),
  },
]

export function DataDeletionPage() {
  return (
    <LegalPage
      title="Account and data deletion"
      summary="How to delete your AstraStore account, exactly what is removed, and what is kept."
      updated="2026-08-14"
      sections={sections}
    />
  )
}
