# Security Policy

The OS2rollekatalog maintainers take the security of our software and our users seriously. Thank you for helping us keep the project and its community safe.

This document explains which versions receive security updates, how to report a vulnerability, and what you can expect from us after you do.

## Supported Versions

Security fixes are provided only for the versions listed as supported below. If you are using an older version, please upgrade and check whether the issue still exists before reporting.

| Version    | Supported                                        |
| ---------- | ------------------------------------------------ |
| 2026r5     | :white_check_mark: Yes                           |
| 2026r4     | :white_check_mark: Yes                           |
| [< 2026r4] | :x: No                                           |

## Reporting a Vulnerability

**Please do not report security vulnerabilities through public GitHub issues, discussions, or pull requests.** Public reports put users at risk before a fix is available.

Instead, please report them privately through this channel:

2. **Email.** Send your report to **[kontakt@digital-identity.dk]**

### What to include in your report

To help us triage and fix the issue quickly, please include as much of the following as you can:

- The type of issue (for example: remote code execution, SQL injection, cross-site scripting, authentication bypass, information disclosure).
- The affected version(s), component, and the full paths of relevant source files, including the branch, tag, or commit.
- Any special configuration required to reproduce the issue.
- Step-by-step instructions to reproduce the issue.
- Proof-of-concept or exploit code, if available.
- The impact of the issue, including how an attacker might exploit it.
- Any suggested mitigation or fix, if you have one.
- Whether and how you would like to be credited.

Please submit one vulnerability per report, and write in English if possible.

## What to Expect

After you submit a report, we aim to follow this timeline:

| Stage                                          | Target timeframe                    |
| ---------------------------------------------- | ----------------------------------- |
| Acknowledgement of your report                 | Within [3] business days            |
| Initial assessment (validity and severity)     | Within [5] business days            |
| Progress updates                               | At least every [7] days             |
| Fix released for critical/high severity issues | Within [14] days of confirmation    |

We will keep you informed if we expect a delay. If you have not received an acknowledgement within [3] business days, please follow up at [security@os2.eu]

Our handling process is:

1. **Confirm** the issue and determine the affected versions.
2. **Assess** the severity, using [CVSS v3.1] as a guide.
3. **Develop** a fix.
4. **Release** patched versions for all supported release lines.
5. **Publish** a security advisory describing the issue, affected versions, the fix, and credit to the reporter.

## Disclosure Policy

We follow a **coordinated vulnerability disclosure** process. We ask that you:

- Give us a reasonable amount of time to investigate and release a fix before any public disclosure.
- Keep details of the vulnerability confidential until a fix is released or until [30] days have passed since your report, whichever comes first. We may agree on a different timeline with you depending on the complexity of the issue.

If a vulnerability is being actively exploited, we may accelerate the release of a fix and advisory.

## Scope

**In scope:**

- Source code in this repository.

**Out of scope:**

- Vulnerabilities in third-party dependencies. Please report these to the upstream project. If a dependency vulnerability is exploitable through OS2rollekatalog, we still want to hear about it.
- Versions marked as unsupported above.
- Issues that require a compromised host, physical access, or a malicious administrator.
- Configurations that our documentation explicitly marks as insecure or intended for development only.
- Volumetric denial-of-service attacks.
- Social engineering of maintainers, contributors, or users.
- Reports from automated scanners without a demonstrated, exploitable impact.
- Missing hardening measures or best practices without a demonstrated security impact.

## Safe Harbor

We consider security research conducted in good faith and in line with this policy to be authorized, and we will not pursue or support legal action against you for it. Good faith means that you:

- Test only against your own installations, not against systems or data belonging to others.
- Avoid privacy violations, data destruction, and disruption of services.
- Access no more data than necessary to demonstrate the issue.
- Report the vulnerability to us promptly and follow the disclosure policy above.

This commitment covers only the OS2rollekatalog project and its maintainers; we cannot authorize testing on behalf of third parties.

## Recognition

We do not currently offer a paid bug bounty. With your permission, we will gladly credit you in the security advisory and release notes. If you prefer to remain anonymous, just let us know.

