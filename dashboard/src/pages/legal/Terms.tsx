import { Link } from 'react-router-dom'
import { LegalPage, Placeholder, type LegalSection } from '../../components/legal/LegalPage'

const sections: LegalSection[] = [
  {
    id: 'agreement',
    title: 'The agreement',
    body: (
      <>
        <p>
          These terms govern your use of AstraStore, operated by{' '}
          <Placeholder>[LEGAL ENTITY NAME]</Placeholder>. By creating an account you
          accept them. If you do not accept them, do not create an account.
        </p>
        <p>
          If you are using AstraStore for an organisation, you confirm you are
          authorised to accept these terms on its behalf.
        </p>
      </>
    ),
  },
  {
    id: 'account',
    title: 'Your account',
    body: (
      <>
        <ul className="ml-5 list-disc space-y-1.5">
          <li>You must provide a valid email address and keep it current.</li>
          <li>
            You are responsible for everything done through your account, including
            through any API key you issue.
          </li>
          <li>
            Keep your password and API keys secret. An API key is shown once at
            creation; if you lose it, revoke it and issue a new one.
          </li>
          <li>
            Tell us promptly at <Placeholder>[SECURITY CONTACT EMAIL]</Placeholder> if
            you believe your account or a key has been compromised.
          </li>
          <li>
            You must be at least <Placeholder>[MINIMUM AGE]</Placeholder> years old.
          </li>
        </ul>
      </>
    ),
  },
  {
    id: 'acceptable-use',
    title: 'Acceptable use',
    body: (
      <>
        <p>You may not use AstraStore to:</p>
        <ul className="ml-5 list-disc space-y-1.5">
          <li>Store or distribute content that is unlawful where you or we operate.</li>
          <li>
            Store or distribute content you do not have the right to store or
            distribute.
          </li>
          <li>Distribute malware, or host infrastructure for attacks on others.</li>
          <li>
            Attempt to access another account's data, or to circumvent authentication,
            authorisation or rate limiting.
          </li>
          <li>
            Probe or load-test the platform without our written permission, or use it in
            a way that degrades the service for others.
          </li>
          <li>Resell the service as your own without a written agreement with us.</li>
        </ul>
        <p>
          We do not monitor the content of your objects. We act on acceptable-use
          matters when they are reported to us or when our operational telemetry shows
          the platform being abused.
        </p>
      </>
    ),
  },
  {
    id: 'your-content',
    title: 'Your content',
    body: (
      <>
        <p>
          <strong className="font-semibold text-ink">Your content remains yours.</strong>{' '}
          We claim no ownership of anything you upload.
        </p>
        <p>
          You grant us only the permission technically required to run the service: to
          store your content, to replicate it across storage nodes for durability, and
          to transmit it back to you or to whoever you authorise. That permission ends
          when you delete the content.
        </p>
        <p>
          You are responsible for having the rights to the content you upload, and for
          keeping your own backups of anything you cannot afford to lose.
        </p>
      </>
    ),
  },
  {
    id: 'availability',
    title: 'Availability and durability',
    body: (
      <>
        <p>
          We aim to keep AstraStore available and your data intact, and we replicate
          every object across multiple storage nodes to survive hardware failure.
        </p>
        <p>
          <strong className="font-semibold text-ink">
            We do not offer a service level agreement unless one is stated in a separate
            written contract with you.
          </strong>{' '}
          The service is provided on an “as is” and “as available” basis. Maintenance,
          faults and outages will happen.
        </p>
        <p>
          Replication protects against hardware failure. It does not protect against you
          deleting something. Keep your own backups.
        </p>
      </>
    ),
  },
  {
    id: 'suspension',
    title: 'Suspension and termination',
    body: (
      <>
        <p>
          We may suspend or terminate an account that breaches these terms, that is
          being used to harm the platform or its users, or where we are legally required
          to do so. Where circumstances allow, we will tell you why and give you an
          opportunity to put it right.
        </p>
        <p>
          You may delete your account at any time from Settings. See{' '}
          <Link to="/data-deletion" className="text-accent-text underline underline-offset-2">
            Account and data deletion
          </Link>{' '}
          for exactly what that removes.
        </p>
      </>
    ),
  },
  {
    id: 'liability',
    title: 'Liability',
    body: (
      <>
        <p>
          To the fullest extent permitted by law, we are not liable for indirect or
          consequential loss, for loss of profit or revenue, or for loss of data to the
          extent it could have been avoided by keeping your own backup.
        </p>
        <p>
          Our total liability arising from your use of the service is limited to{' '}
          <Placeholder>[LIABILITY CAP]</Placeholder>.
        </p>
        <p>
          Nothing in these terms limits liability that cannot lawfully be limited,
          including for death or personal injury caused by negligence, or for fraud.
        </p>
      </>
    ),
  },
  {
    id: 'changes',
    title: 'Changes to the service and these terms',
    body: (
      <p>
        We may change the service and these terms. For material changes we will update
        the date at the top of this page and notify account holders at{' '}
        <Placeholder>[NOTIFICATION METHOD]</Placeholder> before they take effect.
        Continuing to use AstraStore after that date means you accept the revised terms.
      </p>
    ),
  },
  {
    id: 'law',
    title: 'Governing law',
    body: (
      <p>
        These terms are governed by the laws of{' '}
        <Placeholder>[GOVERNING JURISDICTION]</Placeholder>, and disputes are subject to
        the exclusive jurisdiction of its courts.
      </p>
    ),
  },
]

export function TermsPage() {
  return (
    <LegalPage
      title="Terms of Service"
      summary="The rules for using AstraStore, what we commit to, and what we do not."
      updated="2026-08-14"
      sections={sections}
    />
  )
}
