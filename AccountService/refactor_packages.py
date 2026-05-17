import os
import shutil
import re

base_dir = '/Users/mac2019/Desktop/banking-pos/AccountService/src/main/java/com/account'

features = {
    'management': {
        'model': ['Account.java', 'AccountHold.java', 'AccountStatus.java', 'AccountType.java', 'AccountSubType.java', 'HoldType.java'],
        'controller': ['AccountController.java', 'MyAccountController.java', 'HealthController.java'],
        'service': ['AccountService.java'],
        'repository': ['AccountRepository.java', 'AccountHoldRepository.java'],
        'mapper': ['AccountMapper.java']
    },
    'transaction': {
        'model': ['Transaction.java', 'TransactionLedgerEntry.java', 'TransactionStatus.java', 'TransactionType.java'],
        'controller': ['TransactionController.java'],
        'service': ['TransactionService.java', 'TransactionLedgerService.java', 'TransactionSpecifications.java'],
        'repository': ['TransactionRepository.java', 'TransactionLedgerRepository.java'],
        'mapper': ['TransactionMapper.java']
    }
}

file_to_new_pkg = {}

# create directories and move files
for feature, layers in features.items():
    for layer, files in layers.items():
        new_pkg_dir = os.path.join(base_dir, feature, layer)
        os.makedirs(new_pkg_dir, exist_ok=True)
        new_pkg = f"com.account.{feature}.{layer}"
        
        for file in files:
            old_path = os.path.join(base_dir, layer, file)
            if os.path.exists(old_path):
                new_path = os.path.join(new_pkg_dir, file)
                shutil.move(old_path, new_path)
                file_to_new_pkg[file.replace('.java', '')] = new_pkg

# update package and imports in all java files
def update_file(path):
    with open(path, 'r') as f:
        content = f.read()

    # update package declaration
    # Find the current file name to know its new package
    filename = os.path.basename(path).replace('.java', '')
    if filename in file_to_new_pkg:
        new_pkg = file_to_new_pkg[filename]
        content = re.sub(r'package com\.account\.[a-z]+;', f'package {new_pkg};', content)

    # update imports
    # Example: import com.account.model.Account; -> import com.account.management.model.Account;
    for cls, new_pkg in file_to_new_pkg.items():
        # Match old import
        old_import_pattern = rf'import com\.account\.[a-z]+\.{cls};'
        new_import = f'import {new_pkg}.{cls};'
        content = re.sub(old_import_pattern, new_import, content)

    # update wildcard imports if any (though usually we don't have them or we can just replace the whole string)
    # Actually, wildcard imports like import com.account.model.*; are tricky. 
    # Let's see if there are any.
    content = content.replace('import com.account.model.*;', 'import com.account.management.model.*;\nimport com.account.transaction.model.*;')
    
    with open(path, 'w') as f:
        f.write(content)

for root, dirs, files in os.walk(base_dir):
    for file in files:
        if file.endswith('.java'):
            update_file(os.path.join(root, file))

# Remove empty old directories
for layer in ['model', 'controller', 'service', 'repository', 'mapper']:
    old_dir = os.path.join(base_dir, layer)
    if os.path.exists(old_dir) and not os.listdir(old_dir):
        os.rmdir(old_dir)

print("Refactoring complete.")
