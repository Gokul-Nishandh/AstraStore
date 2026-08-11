from setuptools import setup, find_packages

setup(
    name="astrastore",
    version="1.0.0",
    description="Python SDK for AstraStore Distributed Storage System",
    author="Gokul Nishandh S T",
    packages=find_packages(),
    install_requires=[
        "urllib3>=1.26.0",
    ],
    python_requires=">=3.8",
)
