# Git and GitHub guide for CodeSentinel

This repo already has real commits made while building the project. Check with `git log --oneline`; do not rewrite or backdate them (your viva/evaluator may look at the history).

## 1. One-time setup
1. Install Git (https://git-scm.com/downloads). Check: `git --version`.
2. Tell Git who you are (use your real name/email, they appear in your commits):
   `git config --global user.name "Your Name"`
   `git config --global user.email "you@example.com"`
3. Create a free GitHub account, then an **empty** repository named `codesentinel` (no README, no .gitignore, so it does not conflict).

## 2. Check that no secrets will be uploaded
From the project folder:
```
git status
git check-ignore -v .env          # must print a .gitignore rule
git ls-files | grep -E "^\.env$"  # must print nothing
```
`.env` (database password, JWT secret) is git-ignored; only `.env.example` with placeholders is tracked. If you ever commit a real secret by mistake, change that secret immediately; deleting the file later does not remove it from history.

## 3. Push to GitHub
```
git branch -M main
git remote add origin https://github.com/<your-username>/codesentinel.git
git push -u origin main
```
GitHub asks for authentication: use a Personal Access Token (Settings -> Developer settings) as the password, or install GitHub CLI and run `gh auth login`.

## 4. Getting it on another machine
```
git clone https://github.com/<your-username>/codesentinel.git
cd codesentinel
cp .env.example .env     # then fill in real values
```
Then follow the README (Docker, Postgres, `mvn`, `npm`).

## 5. Everyday workflow
```
git status                 # what changed
git diff                   # see the changes
git add <files>            # stage
git commit -m "feat: short description"
git push
git log --oneline          # history
```
Use small commits with clear messages. Run the tests before committing.

## 6. Honest-history rules
- Commits must reflect work actually done; never fake dates or authors.
- If your course requires a minimum number of commits, they come from real work, not from splitting or inventing commits.
