import { Link } from 'react-router-dom'
import { LegalPage, Placeholder, type LegalSection } from '../../components/legal/LegalPage'

const sections: LegalSection[] = [
  {
    id: 'who',
    title: 'Who we are',
    body: (
      <>
        <p>
          AstraStore is a distributed object storage platform operated by{' '}
          <Placeholder>[LEGAL ENTITY NAME]</Placeholder>, registered at{' '}
          <Placeholder>[REGISTERED ADDRESS]</Placeholder>. In this policy, “we” and
          “us” mean that entity, and “you” means the holder of an AstraStore account.
        </p>
        <p>
          For any question about this policy or your data, contact{' '}
          <Placeholder>[PRIVACY CONTACT EMAIL]</Placeholder>.
        </p>
      </>
    ),
  },
  {
    id: 'collected',
    title: 'What we collect',
    body: (
      <>
        <p>We collect only what the service needs to function.</p>
        <p className="font-medium text-ink">Account information</p>
        <ul className="ml-5 list-disc space-y-1.5">
          <li>Your username and email address, supplied when you register.</li>
          <li>
            A cryptographic hash of your password. We never store, log or transmit your
            password itself, and we cannot recover it.
          </li>
          <li>The roles assigned to your account.</li>
        </ul>
        <p className="font-medium text-ink">Content you upload</p>
        <ul className="ml-5 list-disc space-y-1.5">
          <li>
            The objects you upload, together with their names, sizes, content types and
            content checksums.
          </li>
          <li>The buckets you create and the structure you organise them into.</li>
        </ul>
        <p className="font-medium text-ink">Security and operational records</p>
        <ul className="ml-5 list-disc space-y-1.5">
          <li>
            Security events — sign-ins, failed sign-in attempts, API key issuance and
            revocation, permission changes, account deletion — each recorded with the
            originating IP address, the browser or client user agent, and a timestamp.
          </li>
          <li>
            Operational telemetry about the platform itself: service availability,
            response times and storage capacity. This describes the system, not you.
          </li>
        </ul>
        <p>
          We do not use advertising trackers, third-party analytics or cross-site
          cookies. The application stores your session tokens and your theme preference
          in your browser's local storage; nothing is shared with a third party.
        </p>
      </>
    ),
  },
  {
    id: 'content',
    title: 'How we handle your content',
    body: (
      <>
        <p>
          Objects you upload are split into chunks, checksummed, and replicated across
          multiple storage nodes so that a single node failure does not lose your data.
          Replication means more than one copy of your content exists at any time; this
          is a durability measure, not a separate use of your data.
        </p>
        <p>
          <strong className="font-semibold text-ink">We do not read, scan, index or analyse the contents of your objects.</strong>{' '}
          We do not use your content to train machine-learning models. We do not sell
          your data, and we do not share it with third parties for their own purposes.
        </p>
        <p>
          Access to your objects is scoped to your account. Every request is checked
          against the ownership of the object requested.
        </p>
      </>
    ),
  },
  {
    id: 'why',
    title: 'Why we process it',
    body: (
      <>
        <ul className="ml-5 list-disc space-y-1.5">
          <li>
            <strong className="font-medium text-ink">To provide the service</strong> —
            storing, replicating and returning the objects you ask for. Without this we
            have no product.
          </li>
          <li>
            <strong className="font-medium text-ink">To keep accounts secure</strong> —
            the security log is how we detect credential-stuffing, brute-force attempts
            and misuse of API keys.
          </li>
          <li>
            <strong className="font-medium text-ink">To keep the platform running</strong>{' '}
            — availability and capacity telemetry tells us when something is failing.
          </li>
        </ul>
        <p>
          Where <Placeholder>[APPLICABLE DATA PROTECTION LAW]</Placeholder> requires a
          lawful basis, we rely on performance of our contract with you for the first,
          and our legitimate interest in operating a secure service for the others.
        </p>
      </>
    ),
  },
  {
    id: 'retention',
    title: 'How long we keep it',
    body: (
      <>
        <ul className="ml-5 list-disc space-y-1.5">
          <li>
            <strong className="font-medium text-ink">Your objects</strong> — until you
            delete them. Deleted objects go to Trash and are destroyed when you empty it.
          </li>
          <li>
            <strong className="font-medium text-ink">Account information</strong> — until
            you delete your account.
          </li>
          <li>
            <strong className="font-medium text-ink">Security audit records</strong> —
            retained for 90 days, after which they are moved out of the live system.
            When an account is deleted, its entries are anonymised rather than removed;
            see{' '}
            <Link to="/data-deletion" className="text-accent-text underline underline-offset-2">
              Account and data deletion
            </Link>{' '}
            for why.
          </li>
        </ul>
      </>
    ),
  },
  {
    id: 'rights',
    title: 'Your rights',
    body: (
      <>
        <p>You can, at any time and without contacting us:</p>
        <ul className="ml-5 list-disc space-y-1.5">
          <li>Download any object you have uploaded.</li>
          <li>Delete any object, or all of them.</li>
          <li>Change your username and email address in Settings.</li>
          <li>Review your own security history in the audit view.</li>
          <li>Delete your account entirely.</li>
        </ul>
        <p>
          Depending on where you live you may also have rights to access, correct,
          export, restrict or object to our processing of your data. To exercise any of
          these, contact <Placeholder>[PRIVACY CONTACT EMAIL]</Placeholder>. We will
          respond within <Placeholder>[RESPONSE WINDOW]</Placeholder>.
        </p>
      </>
    ),
  },
  {
    id: 'security',
    title: 'How we protect it',
    body: (
      <>
        <ul className="ml-5 list-disc space-y-1.5">
          <li>Passwords are stored only as salted hashes.</li>
          <li>
            API keys are stored hashed. The full key is shown once, at creation, and
            never again — we cannot recover it for you.
          </li>
          <li>
            Every request is authenticated and authorised at the API gateway before it
            reaches a service holding data.
          </li>
          <li>Objects are checksummed on write and verified on read.</li>
          <li>Sessions expire, and refresh tokens are revoked on sign-out.</li>
        </ul>
        <p>
          No system is perfectly secure. If we become aware of a breach affecting your
          data we will notify you and, where required, the relevant supervisory
          authority, within the period required by law.
        </p>
      </>
    ),
  },
  {
    id: 'where',
    title: 'Where your data is held',
    body: (
      <p>
        Your data is stored on infrastructure located in{' '}
        <Placeholder>[HOSTING REGION(S)]</Placeholder>, operated by{' '}
        <Placeholder>[HOSTING PROVIDER]</Placeholder>. If you are outside that region,
        your data will be transferred there in order to provide the service.
      </p>
    ),
  },
  {
    id: 'changes',
    title: 'Changes to this policy',
    body: (
      <p>
        If we change this policy materially we will update the date at the top of this
        page and notify account holders at{' '}
        <Placeholder>[NOTIFICATION METHOD]</Placeholder> before the change takes effect.
      </p>
    ),
  },
]

export function PrivacyPage() {
  return (
    <LegalPage
      title="Privacy Policy"
      summary="What AstraStore collects, why, how long it is kept, and what you can do about it."
      updated="2026-08-14"
      sections={sections}
    />
  )
}
