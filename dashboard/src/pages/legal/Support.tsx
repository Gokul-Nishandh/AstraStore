import { Link } from 'react-router-dom'
import { LegalPage, Placeholder, type LegalSection } from '../../components/legal/LegalPage'

const sections: LegalSection[] = [
  {
    id: 'contact',
    title: 'How to reach us',
    body: (
      <>
        <ul className="ml-5 list-disc space-y-1.5">
          <li>
            <strong className="font-medium text-ink">General support</strong> —{' '}
            <Placeholder>[SUPPORT EMAIL]</Placeholder>. We aim to respond within{' '}
            <Placeholder>[RESPONSE WINDOW]</Placeholder>.
          </li>
          <li>
            <strong className="font-medium text-ink">Security reports</strong> —{' '}
            <Placeholder>[SECURITY CONTACT EMAIL]</Placeholder>. See below.
          </li>
          <li>
            <strong className="font-medium text-ink">Privacy and data requests</strong> —{' '}
            <Placeholder>[PRIVACY CONTACT EMAIL]</Placeholder>.
          </li>
          <li>
            <strong className="font-medium text-ink">Postal</strong> —{' '}
            <Placeholder>[REGISTERED ADDRESS]</Placeholder>.
          </li>
        </ul>
      </>
    ),
  },
  {
    id: 'before',
    title: 'Before you write to us',
    body: (
      <>
        <p>These are the things we are asked about most, with the answer.</p>
        <p className="font-medium text-ink">I have lost my API key</p>
        <p>
          We cannot recover it. Keys are stored hashed and the full value is shown only
          once, at creation. Revoke the old key and issue a new one from the API keys
          screen.
        </p>
        <p className="font-medium text-ink">I deleted an object by mistake</p>
        <p>
          Deleting an object moves it to Trash, where you can restore it. If you emptied
          the Trash, the data is gone — that operation destroys the stored bytes and we
          cannot reverse it.
        </p>
        <p className="font-medium text-ink">I have forgotten my password</p>
        <p>
          Use the reset link on the{' '}
          <Link to="/login" className="text-accent-text underline underline-offset-2">
            sign-in page
          </Link>
          . We cannot tell you your password; we only store a hash of it.
        </p>
        <p className="font-medium text-ink">I cannot see the cluster or audit pages</p>
        <p>
          Those require an administrator role. Ask an administrator on your deployment to
          change your role, or check your current roles in Settings.
        </p>
      </>
    ),
  },
  {
    id: 'reporting-bugs',
    title: 'Reporting a problem',
    body: (
      <>
        <p>To help us fix something quickly, include:</p>
        <ul className="ml-5 list-disc space-y-1.5">
          <li>What you were trying to do, and what happened instead.</li>
          <li>The approximate time, with your timezone.</li>
          <li>The exact message shown on screen.</li>
          <li>Your browser and operating system, or your CLI/SDK version.</li>
        </ul>
        <p>
          Please do not send us passwords or API keys. We will never ask you for either.
        </p>
      </>
    ),
  },
  {
    id: 'security',
    title: 'Reporting a security issue',
    body: (
      <>
        <p>
          If you believe you have found a vulnerability, report it privately to{' '}
          <Placeholder>[SECURITY CONTACT EMAIL]</Placeholder> rather than raising it in
          public. We will acknowledge within{' '}
          <Placeholder>[SECURITY ACKNOWLEDGEMENT WINDOW]</Placeholder>.
        </p>
        <p>
          Please give us a reasonable opportunity to fix an issue before disclosing it.
          Do not access, modify or delete data belonging to anyone else while
          investigating; testing against your own account is fine, testing against other
          people's is not.
        </p>
      </>
    ),
  },
  {
    id: 'docs',
    title: 'Documentation',
    body: (
      <p>
        The API, CLI and SDK reference material lives at{' '}
        <Placeholder>[DOCUMENTATION URL]</Placeholder>. If something there is wrong or
        missing, tell us — documentation faults are treated as faults.
      </p>
    ),
  },
]

export function SupportPage() {
  return (
    <LegalPage
      title="Support"
      summary="How to get help with AstraStore, and how to report a problem or a security issue."
      updated="2026-08-14"
      sections={sections}
    />
  )
}
